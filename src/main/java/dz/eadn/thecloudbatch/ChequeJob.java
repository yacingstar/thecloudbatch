package dz.eadn.thecloudbatch;

import javax.sql.DataSource;
import java.io.File;

import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.*;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import dz.eadn.thecloudbatch.model.Cheque;

@Configuration
public class ChequeJob {

	// dbStep
    @Bean
    @StepScope
    public MultiResourceItemReader<Cheque> multiChequeReader(
            @Value("#{jobParameters['remisesDir']}") String remisesDir) {
        MultiResourceItemReader<Cheque> reader = new MultiResourceItemReader<>();
        File dir = new File(remisesDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".remise") && !name.endsWith(".remise.DONE"));
        Resource[] resources = new Resource[files != null ? files.length : 0];
        for (int i = 0; i < resources.length; i++) {
            resources[i] = new FileSystemResource(files[i]);
        }
        reader.setResources(resources);
        reader.setDelegate(chequeFileReader());
        return reader;
    }
    
    // dbStep
    @Bean
    @StepScope
    public FlatFileItemReader<Cheque> chequeFileReader() {
        BeanWrapperFieldSetMapper<Cheque> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(Cheque.class);
        fieldSetMapper.setDistanceLimit(2);
        return new FlatFileItemReaderBuilder<Cheque>()
                .name("chequeReader")
                .linesToSkip(1)
                .delimited()
                .delimiter(".")
                .names("rio", "operation_type", "beneficiary_rib", 
                        "beneficiary_bank","cheque_number", "sender_rib", "sender_bank", "amount")
                .fieldSetMapper(fieldSetMapper)
                .build();
    }

    // fileStep
    @Bean
    @StepScope
    public JdbcCursorItemReader<Cheque> databaseChequeReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<Cheque>()
                .name("databaseChequeReader")
                .dataSource(dataSource)
                .sql("SELECT * FROM cheques ORDER BY id")
                .rowMapper(new BeanPropertyRowMapper<>(Cheque.class))
                .build();
    }
    
    @Bean
    @StepScope
    public ItemProcessor<Cheque, Cheque> toBeIntegratedProcessor() {
        return item -> {
            if ("to be integrated".equals(item.getStatus())) {
                return item;
            }
            return null; // Skip items that don't match
        };
    }



    // dbStep
    @Bean
    public JdbcBatchItemWriter<Cheque> chequeJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Cheque>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("""
                    INSERT INTO cheques (
                        id,
                        cheque_number,
                        rio,
                        operation_type,
                        beneficiary_rib,
                        beneficiary_bank,
                        sender_rib,
                        sender_bank,
                        amount,
                        status
                    ) VALUES (
                        cheque_sequence.NEXTVAL,
                        :cheque_number,
                        :rio,
                        :operation_type,
                        :beneficiary_rib,
                        :beneficiary_bank,
                        :sender_rib,
                        :sender_bank,
                        :amount,
                        :status
                    )
                """)
                .dataSource(dataSource)
                .build();
    }

    // fileStep
    @Bean
    @StepScope
    public CustomItemWriter dynamicChequeFileWriter(
            @Value("#{jobParameters['outputDirectory'] ?: '/output'}") String outputDirectory) {
        return new CustomItemWriter();
    }
    

    // dbStep
    @Bean
    @StepScope
    public ItemProcessor<Cheque, Cheque> chequeProcessor() {
        return item -> {
            // Set default status for new cheques
            if (item.getStatus() == null || item.getStatus().isEmpty()) {
                item.setStatus("to be integrated");
            }
            return item;
        };
    }

//    // fileStep
    
    @Bean
    @StepScope
    public FileMarkingTasklet fileMarkingTasklet(
            @Value("#{jobParameters['remisesDir']}") String remisesDir) {
        return new FileMarkingTasklet(remisesDir);
    }

    @Bean
    public Step dbStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MultiResourceItemReader<Cheque> multiChequeReader,
            ItemProcessor<Cheque, Cheque> chequeProcessor,
            JdbcBatchItemWriter<Cheque> chequeJdbcWriter
    ) {
        return new StepBuilder("dbStep", jobRepository)
                .<Cheque, Cheque>chunk(10, transactionManager)
                .reader(multiChequeReader)
                .processor(chequeProcessor)
                .writer(chequeJdbcWriter)
                .build();
    }

    @Bean
    public Step markFilesStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FileMarkingTasklet fileMarkingTasklet
    ) {
        return new StepBuilder("markFilesStep", jobRepository)
                .tasklet(fileMarkingTasklet, transactionManager)
                .build();
    }
    
    @Bean
    @StepScope
    public JdbcBatchItemWriter<Cheque> statusUpdateWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Cheque>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("UPDATE cheques SET status = 'integrated' WHERE id = :id")
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public Step updateStatusStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<Cheque> databaseChequeReader,
            ItemProcessor<Cheque, Cheque> toBeIntegratedProcessor,
            JdbcBatchItemWriter<Cheque> statusUpdateWriter
    ) {
        return new StepBuilder("updateStatusStep", jobRepository)
                .<Cheque, Cheque>chunk(10, transactionManager)
                .reader(databaseChequeReader)
                .processor(toBeIntegratedProcessor)
                .writer(statusUpdateWriter)
                .build();
    }


    @Bean
    public Step fileStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<Cheque> databaseChequeReader,
            ItemProcessor<Cheque, Cheque> toBeIntegratedProcessor,
            CustomItemWriter dynamicChequeFileWriter
    ) {
        return new StepBuilder("fileStep", jobRepository)
                .<Cheque, Cheque>chunk(10, transactionManager)
                .reader(databaseChequeReader)
                .processor(toBeIntegratedProcessor)
                .writer(dynamicChequeFileWriter)
                .build();
    }
    

    

    @Bean
    public Job chequeJobThing(
            JobRepository jobRepository,
            Step dbStep,
            Step markFilesStep,
            Step fileStep,
            Step updateStatusStep
    ) {
        return new JobBuilder("chequeJob", jobRepository)
                .start(dbStep)
                .next(markFilesStep)
                .next(fileStep)
                .next(updateStatusStep) 
                .build();
    }
}