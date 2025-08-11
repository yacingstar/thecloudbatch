package dz.eadn.thecloudbatch;

import java.io.File;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import dz.eadn.thecloudbatch.model.Cheque;

@Configuration
public class CraWriterJob {
	
	@Bean
	@StepScope
	public FlatFileItemReader<Cheque> craFileReader() {
	    BeanWrapperFieldSetMapper<Cheque> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
	    fieldSetMapper.setTargetType(Cheque.class);
	    fieldSetMapper.setDistanceLimit(2);
	    
	    // Find the first .CRA file in the output directory
	    File outputDir = new File(System.getProperty("user.dir") + "/output");
	    File[] craFiles = outputDir.listFiles((dir, name) -> name.endsWith(".CRA"));
	    
	    String craFilePath;
	    if (craFiles != null && craFiles.length > 0) {
	        craFilePath = craFiles[0].getAbsolutePath(); // Use the first CRA file found
	    } else {
	        throw new RuntimeException("No .CRA file found in output directory");
	    }
	    
	    return new FlatFileItemReaderBuilder<Cheque>()
	            .name("craFileReader")
	            .resource(new FileSystemResource(craFilePath))  // Point to actual CRA file
	            .linesToSkip(1)
	            .delimited()
	            .delimiter(".")
	            .names("rio", "operation_type", "beneficiary_rib", "beneficiary_bank", "cheque_number", "sender_rib", "sender_bank", "amount")
	            .fieldSetMapper(fieldSetMapper)
	            .build();
	}

	@Bean
	@StepScope
	public ItemProcessor<Cheque, Cheque> craValidationProcessor(DataSource dataSource) {
	    return new ItemProcessor<Cheque, Cheque>() {
	        @Override
	        public Cheque process(Cheque craItem) throws Exception {
	            // Find matching cheque in database with "integrated" status
	            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
	            
	            String sql = """
	                SELECT * FROM cheques 
	                WHERE rio = ? AND operation_type = ? AND beneficiary_rib = ? 
	                AND beneficiary_bank = ? AND cheque_number = ? AND sender_rib = ? 
	                AND sender_bank = ? AND amount = ? AND status = 'integrated'
	                """;
	            
	            try {
	                Cheque dbCheque = jdbcTemplate.queryForObject(sql, 
	                    new BeanPropertyRowMapper<>(Cheque.class),
	                    craItem.getRio(), craItem.getOperation_type(), craItem.getBeneficiary_rib(),
	                    craItem.getBeneficiary_bank(), craItem.getCheque_number(), craItem.getSender_rib(),
	                    craItem.getSender_bank(), craItem.getAmount());
	                
	                // Return the database cheque (with ID) to update its status
	                return dbCheque;
	            } catch (EmptyResultDataAccessException e) {
	                // No matching cheque found - skip this item
	                return null;
	            }
	        }
	    };
	}

	@Bean
	@StepScope
	public JdbcBatchItemWriter<Cheque> processedStatusWriter(DataSource dataSource) {
	    return new JdbcBatchItemWriterBuilder<Cheque>()
	            .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
	            .sql("UPDATE cheques SET status = 'processed' WHERE id = :id")
	            .dataSource(dataSource)
	            .build();
	}

	@Bean
	public Step craValidationStep(
	        JobRepository jobRepository,
	        PlatformTransactionManager transactionManager,
	        FlatFileItemReader<Cheque> craFileReader,
	        ItemProcessor<Cheque, Cheque> craValidationProcessor,
	        JdbcBatchItemWriter<Cheque> processedStatusWriter
	) {
	    return new StepBuilder("craValidationStep", jobRepository)
	            .<Cheque, Cheque>chunk(10, transactionManager)
	            .reader(craFileReader)
	            .processor(craValidationProcessor)
	            .writer(processedStatusWriter)
	            .build();
	}

	
    @Bean
    @StepScope
    public ItemProcessor<Cheque, Cheque> integratedProcessor() {
        return item -> {
            if ("integrated".equals(item.getStatus())) {
                return item;
            }
            return null; // Skip items that don't match
        };
    }

    @Bean
    @StepScope
    public CustomItemWriter craFileWriter() {
        return new CustomItemWriter(
            System.getProperty("user.dir") + "/output",  // Project root + /output
            "CRA",
            new String[]{"rio", "operation_type", "beneficiary_rib", "beneficiary_bank", "cheque_number", "sender_rib", "sender_bank", "amount"},
            "rio.operation_type.beneficiary_rib.beneficiary_bank.cheque_number.sender_rib.sender_bank.amount",
            "."
        );
    }


    @Bean
    public Step craWriter(
    		
    					JobRepository jobRepository,
			PlatformTransactionManager transactionManager,
			JdbcCursorItemReader<Cheque> databaseChequeReader,
			ItemProcessor<Cheque, Cheque> integratedProcessor,
			CustomItemWriter craFileWriter
	) {
		return new StepBuilder("craWriter", jobRepository)
				.<Cheque, Cheque>chunk(10, transactionManager)
				.reader(databaseChequeReader)
				.processor(integratedProcessor)
				.writer(craFileWriter)
				.build();
	}
    
    @Bean
    public Job craJob(
            JobRepository jobRepository,
            Step craWriter,
            Step craValidationStep
    ) {
        return new JobBuilder("craJob", jobRepository)
                .start(craWriter)
                .next(craValidationStep)  // Add validation step
                .build();
    }
	
}
