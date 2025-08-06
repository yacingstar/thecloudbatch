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

    // Global lot sequence counter (001-999)
    private static final AtomicInteger globalLotSequence = new AtomicInteger(1);

    // Store info for ORD file
    private String ordBank = null;
    private String ordOperationType = null;
    private int ordLotNumber = -1;

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

            // Store info for ORD file (use the first group as representative)
            if (ordBank == null) {
                ordBank = String.valueOf(cheques.get(0).getBeneficiary_bank());
                ordOperationType = String.format("%03d", cheques.get(0).getOperation_type());
                ordLotNumber = lotNumber;
            }
        }
    }

    // Returns the lot number used for the LOT file
    private int getOrCreateWriter(String key, Cheque sampleCheque) {
        if (!writers.containsKey(key)) {
            // Get next global lot sequence number (001-999, then cycles back to 001)
            int lotSequence = globalLotSequence.getAndUpdate(current -> 
                current >= 999 ? 1 : current + 1);
            
            // New filename format: (banque remettante).000.(numéro de lot).(type d'opération).LOT
            String filename = String.format("%03d.000.%03d.%03d.LOT",
                    sampleCheque.getBeneficiary_bank(),  // banque remettante
                    lotSequence,                         // numéro de lot (001-999)
                    sampleCheque.getOperation_type());   // type d'opération

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
            
            // Store the lot sequence for this writer
            sequenceCounters.put(key, new AtomicInteger(lotSequence));
        }
        
        return sequenceCounters.get(key).get();
    }

    // Write ORD file only once, after all writing is done
    private void writeOrdFileOnce() {
        if (ordBank == null || ordOperationType == null || ordLotNumber == -1) return;

        String lotNumStr = String.format("%03d", ordLotNumber);
        String dateHour = new SimpleDateFormat("yyyyMMddHH").format(new Date());
        String ordFilename = String.format("%s.%s.%s.ORD", ordBank, lotNumStr, dateHour);
        String ordPath = outputDirectory + "/" + ordFilename;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ordPath))) {
            writer.write("command_type.lot_number.operation_type");
            writer.newLine();
            writer.write("INLOT." + lotNumStr + "." + ordOperationType);
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
        // Write ORD file only once per job
        writeOrdFileOnce();
        writers.clear();
        sequenceCounters.clear();
        executionContext = null;
        ordBank = null;
        ordOperationType = null;
        ordLotNumber = -1;
    }
}