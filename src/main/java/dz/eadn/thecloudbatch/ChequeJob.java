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
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.*;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.*;
import org.springframework.transaction.PlatformTransactionManager;

import dz.eadn.thecloudbatch.model.Cheque;

@Configuration
public class ChequeJob {

	@Bean
	@StepScope
	public MultiResourceItemReader<Cheque> multiChequeReader(
	        @Value("#{jobParameters['remisesDir']}") String remisesDir) {
	    MultiResourceItemReader<Cheque> reader = new MultiResourceItemReader<>();
	    File dir = new File(remisesDir);
	    File[] files = dir.listFiles((d, name) -> name.endsWith(".cheque"));
	    Resource[] resources = new Resource[files != null ? files.length : 0];
	    for (int i = 0; i < resources.length; i++) {
	        resources[i] = new FileSystemResource(files[i]);
	    }
	    reader.setResources(resources);
	    reader.setDelegate(chequeFileReader());
	    return reader;
	}

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
                        amount
                    ) VALUES (
                        cheque_sequence.NEXTVAL,
                        :cheque_number,
                        :rio,
                        :operation_type,
                        :beneficiary_rib,
                        :beneficiary_bank,
                        :sender_rib,
                        :sender_bank,
                        :amount
                    )
                """)
                .dataSource(dataSource)
                .build();
    }

    @Bean
    @StepScope
    public CustomItemWriter dynamicChequeFileWriter(
            @Value("#{jobParameters['outputDirectory'] ?: '/output'}") String outputDirectory) {
        return new CustomItemWriter(outputDirectory);
    }

    @Bean
    public Step dbStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MultiResourceItemReader<Cheque> multiChequeReader,
            JdbcBatchItemWriter<Cheque> chequeJdbcWriter
    ) {
        return new StepBuilder("dbStep", jobRepository)
                .<Cheque, Cheque>chunk(10, transactionManager)
                .reader(multiChequeReader)
                .writer(chequeJdbcWriter)
                .build();
    }

    @Bean
    public Step fileStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MultiResourceItemReader<Cheque> multiChequeReader,
            CustomItemWriter dynamicChequeFileWriter
    ) {
        return new StepBuilder("fileStep", jobRepository)
                .<Cheque, Cheque>chunk(10, transactionManager)
                .reader(multiChequeReader)
                .writer(dynamicChequeFileWriter)
                .build();
    }

    @Bean
    public Job chequeJobThing(
            JobRepository jobRepository,
            Step dbStep,
            Step fileStep
    ) {
        return new JobBuilder("chequeJob", jobRepository)
                .start(dbStep)
                .next(fileStep)
                .build();
    }
}