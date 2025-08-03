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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CustomItemWriter implements ItemWriter<Cheque>, ItemStream {

    private final Map<String, FlatFileItemWriter<Cheque>> writers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();
    private final String outputDirectory;
    private ExecutionContext executionContext;

    public CustomItemWriter() {
        this.outputDirectory = "/output";
    }

    public CustomItemWriter(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    public void write(Chunk<? extends Cheque> chunk) throws Exception {
        Map<String, List<Cheque>> groupedCheques = new HashMap<>();

        for (Cheque cheque : chunk) {
            String key = cheque.getBeneficiary_bank() + "_" + cheque.getOperation_type();
            groupedCheques.computeIfAbsent(key, k -> new ArrayList<>()).add(cheque);
        }

        for (Map.Entry<String, List<Cheque>> entry : groupedCheques.entrySet()) {
            String key = entry.getKey();
            List<Cheque> cheques = entry.getValue();

            // Write LOT file and get lot number
            int lotNumber = getOrCreateWriter(key, cheques.get(0));
            FlatFileItemWriter<Cheque> writer = writers.get(key);
            writer.write(new Chunk<>(cheques));

            // Write ORD file
            writeOrdFile(cheques.get(0), lotNumber);
        }
    }

    // Returns the lot number used for the LOT file
    private int getOrCreateWriter(String key, Cheque sampleCheque) {
        AtomicInteger counter = sequenceCounters.computeIfAbsent(key, x -> new AtomicInteger(1));
        int sequenceNumber = counter.get();

        if (!writers.containsKey(key)) {
            String filename = String.format("%s.000.lot%03d.%03d.LOT",
                    sampleCheque.getBeneficiary_bank(),
                    sequenceNumber,
                    sampleCheque.getOperation_type());

            String fullPath = outputDirectory + "/" + filename;

            DelimitedLineAggregator<Cheque> lineAggregator = new DelimitedLineAggregator<>();
            lineAggregator.setDelimiter(".");
            BeanWrapperFieldExtractor<Cheque> fieldExtractor = new BeanWrapperFieldExtractor<>();
            fieldExtractor.setNames(new String[]{
                "rio", "operation_type", "beneficiary_rib",
                "beneficiary_bank", "cheque_number", "sender_rib",
                "sender_bank", "amount"
            });
            lineAggregator.setFieldExtractor(fieldExtractor);

            FlatFileItemWriter<Cheque> writer = new FlatFileItemWriterBuilder<Cheque>()
                    .name("dynamicChequeWriter_" + key)
                    .resource(new FileSystemResource(fullPath))
                    .lineAggregator(lineAggregator)
                    .headerCallback(writer1 -> {
                        try {
                            writer1.write("rio.operation_type.beneficiary_rib.beneficiary_bank.cheque_number.sender_rib.sender_bank.amount");
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to write header", e);
                        }
                    })
                    .append(false)
                    .shouldDeleteIfExists(true)
                    .build();

            try {
                writer.afterPropertiesSet();
                if (executionContext != null) {
                    writer.open(executionContext);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize writer for " + filename, e);
            }

            writers.put(key, writer);
            counter.incrementAndGet();
        }
        return sequenceNumber;
    }

    private void writeOrdFile(Cheque cheque, int lotNumber) {
        short bank = cheque.getBeneficiary_bank();
        String operationType = String.format("%03d", cheque.getOperation_type());
        String lotNumStr = String.format("%03d", lotNumber);

        String dateHour = new SimpleDateFormat("yyyyMMddHH").format(new Date());
        String ordFilename = String.format("%s.%s.%s.ORD", bank, lotNumStr, dateHour);
        String ordPath = outputDirectory + "/" + ordFilename;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ordPath))) {
            writer.write("command_type.lot_number.operation_type");
            writer.newLine();
            writer.write("INLOT." + lotNumStr + "." + operationType);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ORD file: " + ordPath, e);
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        this.executionContext = executionContext;
        for (FlatFileItemWriter<Cheque> writer : writers.values()) {
            writer.open(executionContext);
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        for (FlatFileItemWriter<Cheque> writer : writers.values()) {
            writer.update(executionContext);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        for (FlatFileItemWriter<Cheque> writer : writers.values()) {
            writer.close();
        }
        writers.clear();
        sequenceCounters.clear();
        executionContext = null;
    }
}
