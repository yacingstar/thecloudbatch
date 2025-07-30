package dz.eadn.thecloudbatch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.MultiResourceItemReaderBuilder;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CrJob {

    @Bean
    @StepScope
    public MultiResourceItemReader<String> multiResourceItemReader(
            @Value("#{jobParameters['inputDir']}") String inputDir,
            @Value("#{jobParameters['fileType']}") String fileType) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("file:" + inputDir + "/*." + fileType);

        return new MultiResourceItemReaderBuilder<String>()
                .name("multiResourceItemReader")
                .resources(resources)
                .delegate(flatFileItemReader(null))
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<String> flatFileItemReader(
            @Value("#{stepExecutionContext['fileName']}") String fileName) {
        return new FlatFileItemReaderBuilder<String>()
                .name("flatFileItemReader")
                .lineMapper(new PassThroughLineMapper())
                .build();
    }

    @Bean
    public ItemWriter<String> dbWriter() {
        return items -> {
            // Write items to DB here
        };
    }

    @Bean
    public Step fileToDbStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MultiResourceItemReader<String> multiResourceItemReader,
            ItemWriter<String> dbWriter
    ) {
        return new StepBuilder("fileToDbStep", jobRepository)
                .<String, String>chunk(10, transactionManager)
                .reader(multiResourceItemReader)
                .writer(dbWriter)
                .build();
    }

    @Bean
    public Job fileToDbJob(
            JobRepository jobRepository,
            Step fileToDbStep
    ) {
        return new JobBuilder("fileToDbJob", jobRepository)
                .start(fileToDbStep)
                .build();
    }
}
