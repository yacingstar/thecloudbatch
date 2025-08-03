package dz.eadn.thecloudbatch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableTask
public class ThecloudbatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThecloudbatchApplication.class, args);
        System.out.println("ThecloudbatchApplication started successfully.");
    }

    @Bean
    public CommandLineRunner runJob(JobLauncher jobLauncher, Job chequeJobThing) {
        return args -> {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(chequeJobThing, jobParameters);
        };
    }
}
