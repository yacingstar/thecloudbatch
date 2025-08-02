package dz.eadn.thecloudbatch;

//import org.springframework.batch.core.Job;
//import org.springframework.batch.core.JobParameters;
//import org.springframework.batch.core.JobParametersBuilder;
//import org.springframework.batch.core.launch.JobLauncher;
//import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;

//import java.nio.file.*;

@SpringBootApplication
@EnableTask
public class ThecloudbatchApplication{

//    private final JobLauncher jobLauncher;
//    private final Job chequeJobThing;
//
//    public ThecloudbatchApplication(JobLauncher jobLauncher, Job chequeJobThing) {
//        this.jobLauncher = jobLauncher;
//        this.chequeJobThing = chequeJobThing;
//    }

    public static void main(String[] args) {
        SpringApplication.run(ThecloudbatchApplication.class, args);
        System.out.println("ThecloudbatchApplication started successfully.");
    }

//    @Override
//    public void run(String... args) throws Exception {
//        Path watchDir = Paths.get("./input");
//        Files.createDirectories(watchDir);
//        
//        WatchService watchService = FileSystems.getDefault().newWatchService();
//        watchDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
//
//        System.out.println("Watching for .LOT files in: " + watchDir.toAbsolutePath());
//
//        new Thread(() -> {
//            while (true) {
//                try {
//                    WatchKey key = watchService.take();
//                    for (WatchEvent<?> event : key.pollEvents()) {
//                        Path fileName = (Path) event.context();
//                        if (fileName.toString().endsWith(".LOT")) {
//                            System.out.println("Found .LOT file: " + fileName);
//                            
//                            // Run your existing job with file path as parameter
//                            JobParameters jobParameters = new JobParametersBuilder()
//                                    .addString("filePath", watchDir.resolve(fileName).toString())
//                                    .addLong("startTime", System.currentTimeMillis())
//                                    .toJobParameters();
//                            var jobExecution = jobLauncher.run(chequeJobThing, jobParameters);
//                            
//                            // Only rename if job completed successfully
//                            if (jobExecution.getExitStatus().getExitCode().equals("COMPLETED")) {
//                                Files.move(watchDir.resolve(fileName), 
//                                         watchDir.resolve(fileName.toString() + ".DONE"));
//                                System.out.println("Processed successfully and renamed to: " + fileName + ".DONE");
//                            } else {
//                                System.out.println("Job failed for file: " + fileName + " - Status: " + jobExecution.getExitStatus());
//                            }
//                        }
//                    }
//                    key.reset();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();
//
//        Thread.currentThread().join(); // Keep main thread alive
//    }
}