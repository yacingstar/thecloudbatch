package dz.eadn.thecloudbatch;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import dz.eadn.thecloudbatch.model.Cheque;

@Configuration
public class ChequeJob {

    @Bean
    @StepScope
    FlatFileItemReader<Cheque> chequeReader(@Value("#{jobParameters['filePath']}") String filePath) {
    	BeanWrapperFieldSetMapper<Cheque> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(Cheque.class);
        fieldSetMapper.setDistanceLimit(2);
        return new FlatFileItemReaderBuilder<Cheque>()
                .name("chequeReader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1)
                .delimited()
                .delimiter(".")
                .names("beneficiary_rib", "beneficiary_bank", "cheque_number", "sender_rib", "sender_bank", "amount")
                .fieldSetMapper(fieldSetMapper)
                .build();
    }

    @Bean
    JdbcBatchItemWriter<Cheque> chequeJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Cheque>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("""
            INSERT INTO cheques (
                id,
                cheque_number,
                beneficiary_rib,
                beneficiary_bank,
                sender_rib,
                sender_bank,
                amount
            ) VALUES (
                cheque_sequence.NEXTVAL,
                :cheque_number,
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
    Step chequeStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Cheque> chequeReader,
            JdbcBatchItemWriter<Cheque> chequeJdbcWriter
    ) {
        return new StepBuilder("chequeStep", jobRepository)
                .<Cheque, Cheque>chunk(10, transactionManager)
                .reader(chequeReader)
                .writer(chequeJdbcWriter)
                .build();
    }

    @Bean
    Job chequeJobThing(JobRepository jobRepository, @Qualifier("chequeStep") Step chequeStep) {
        return new JobBuilder("chequeJob", jobRepository)
                .start(chequeStep)
                .build();
    }
}