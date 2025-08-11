package dz.eadn.thecloudbatch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import dz.eadn.thecloudbatch.model.Cheque;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CustomItemWriter implements ItemWriter<Cheque>, ItemStream {

    private final String outputDirectory;
    private final String fileExtension;
    private final String[] fieldNames;
    private final String headerFormat;
    private final String delimiter;

    private final Map<String, FlatFileItemWriter<Cheque>> writers = new HashMap<>();
    private static final AtomicInteger globalLotSequence = new AtomicInteger(1);
    private ExecutionContext executionContext;
    
    // For ORD file - only created if first file is LOT
    private String ordInfo = null;
    private boolean isFirstFileLot = false;

    // Default constructor for LOT files
    public CustomItemWriter() {
        this("/output", "LOT",
             new String[]{"rio", "operation_type", "beneficiary_rib", "beneficiary_bank", "cheque_number", "sender_rib", "sender_bank", "amount"},
             "rio.operation_type.beneficiary_rib.beneficiary_bank.cheque_number.sender_rib.sender_bank.amount",
             ".");
    }

    // Configurable constructor for any file type
    public CustomItemWriter(String outputDirectory, String fileExtension, String[] fieldNames, String headerFormat, String delimiter) {
        this.outputDirectory = outputDirectory;
        this.fileExtension = fileExtension;
        this.fieldNames = fieldNames;
        this.headerFormat = headerFormat;
        this.delimiter = delimiter;
    }

    @Override
    public void write(Chunk<? extends Cheque> chunk) throws Exception {
        Map<String, List<Cheque>> groups = new HashMap<>();
        
        for (Cheque cheque : chunk) {
            String key = cheque.getBeneficiary_bank() + "_" + cheque.getOperation_type();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(cheque);
        }

        for (Map.Entry<String, List<Cheque>> entry : groups.entrySet()) {
            FlatFileItemWriter<Cheque> writer = getOrCreateWriter(entry.getKey(), entry.getValue().get(0));
            writer.write(new Chunk<>(entry.getValue()));
        }
    }

    private FlatFileItemWriter<Cheque> getOrCreateWriter(String key, Cheque sample) {
        if (!writers.containsKey(key)) {
            int lotNumber = globalLotSequence.getAndUpdate(current -> current >= 999 ? 1 : current + 1);

            String filename = new FileNameBuilder()
                    .addBeneficiaryBank(sample.getBeneficiary_bank())
                    .addPart("000")
                    .addLotNumber(lotNumber)
                    .addOperationType(sample.getOperation_type())
                    .extension(fileExtension)
                    .build();

            // Check if this is the first file and if it's a LOT file
            if (writers.isEmpty() && "LOT".equals(fileExtension)) {
                isFirstFileLot = true;
                ordInfo = sample.getBeneficiary_bank() + "|" + lotNumber + "|" + sample.getOperation_type();
            }

            DelimitedLineAggregator<Cheque> aggregator = new DelimitedLineAggregator<>();
            aggregator.setDelimiter(delimiter);
            
            BeanWrapperFieldExtractor<Cheque> extractor = new BeanWrapperFieldExtractor<>();
            extractor.setNames(fieldNames);
            aggregator.setFieldExtractor(extractor);

            FlatFileItemWriter<Cheque> writer = new FlatFileItemWriterBuilder<Cheque>()
                    .name("writer_" + key)
                    .resource(new FileSystemResource(outputDirectory + "/" + filename))
                    .lineAggregator(aggregator)
                    .headerCallback(w -> w.write(headerFormat))
                    .shouldDeleteIfExists(true)
                    .build();

            try {
                writer.afterPropertiesSet();
                if (executionContext != null) writer.open(executionContext);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create writer", e);
            }

            writers.put(key, writer);
        }
        return writers.get(key);
    }

    private void writeOrdFile() {
        // Only write ORD file if first file was LOT
        if (!isFirstFileLot || ordInfo == null) return;

        String[] parts = ordInfo.split("\\|");
        String ordFilename = new FileNameBuilder()
                .addPart(parts[0])
                .addLotNumber(Integer.parseInt(parts[1]))
                .addTimestamp()
                .extension("ORD")
                .build();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputDirectory + "/" + ordFilename))) {
            writer.write("command_type.lot_number.operation_type");
            writer.newLine();
            writer.write("INLOT." + String.format("%03d", Integer.parseInt(parts[1])) + "." + String.format("%03d", Integer.parseInt(parts[2])));
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write ORD file", e);
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        this.executionContext = executionContext;
        writers.values().forEach(w -> w.open(executionContext));
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        writers.values().forEach(w -> w.update(executionContext));
    }

    @Override
    public void close() throws ItemStreamException {
        writers.values().forEach(FlatFileItemWriter::close);
        writeOrdFile(); // Only writes if first file was LOT
        writers.clear();
        ordInfo = null;
        isFirstFileLot = false;
        executionContext = null;
    }
}
