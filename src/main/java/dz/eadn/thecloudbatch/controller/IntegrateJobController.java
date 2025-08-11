package dz.eadn.thecloudbatch.controller;

import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class IntegrateJobController {

    private static final String UPLOAD_BASE_DIR = "uploads/remises/";
    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final String OUTPUT_DIR = PROJECT_ROOT + "/output/";
    private static final String ORD_DIR = PROJECT_ROOT + "/ord/";
    private static final String CRL_DIR = PROJECT_ROOT + "/crl/";

    private JobLauncher jobLauncher;
    private Job chequeJobThing;
    private Job craJob; // Assuming this is defined elsewhere in your application
    private JobRepository jobRepository;
    
    // Store job execution details for monitoring
    private final Map<String, JobStatusInfo> jobStatusMap = new ConcurrentHashMap<>();

    public IntegrateJobController(JobLauncher jobLauncher, Job chequeJobThing, JobRepository jobRepository, Job craJob) {
        this.jobLauncher = jobLauncher;
        this.chequeJobThing = chequeJobThing;
        this.jobRepository = jobRepository;
        this.craJob = craJob;
        
        // Ensure directories exist
        createDirectories();
    }

    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_BASE_DIR));
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            Files.createDirectories(Paths.get(ORD_DIR));
            Files.createDirectories(Paths.get(CRL_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create directories: " + e.getMessage());
        }
    }

    @PostMapping("/api/upload/remises")
    public ResponseEntity<Map<String, String>> uploadRemises(@RequestParam MultipartFile[] files) {
        Map<String, String> response = new HashMap<>();
        List<String> uploadedFiles = new ArrayList<>();
        
        try {
            Path uploadPath = Paths.get(UPLOAD_BASE_DIR);
            Files.createDirectories(uploadPath);

            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String originalFileName = file.getOriginalFilename();
                    
                    Path filePath = uploadPath.resolve(originalFileName);
                    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                    uploadedFiles.add(originalFileName);
                }
            }
            
            response.put("status", "success");
            response.put("filesCount", String.valueOf(files.length));
            response.put("uploadedFiles", String.join(", ", uploadedFiles));
            response.put("directoryPath", uploadPath.toAbsolutePath().toString());
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/api/remises/list")
    public ResponseEntity<List<Map<String, String>>> listRemises() {
        List<Map<String, String>> remises = new ArrayList<>();
        File baseDir = new File(UPLOAD_BASE_DIR);
        
        if (baseDir.exists() && baseDir.isDirectory()) {
            // Get all .remise files directly from the base directory (excluding .DONE files)
            File[] remiseFiles = baseDir.listFiles((dir, name) -> 
                name.endsWith(".remise") && !name.endsWith(".remise.DONE"));
            
            if (remiseFiles != null) {
                for (File file : remiseFiles) {
                    Map<String, String> info = new HashMap<>();
                    info.put("fileName", file.getName());
                    info.put("filePath", file.getAbsolutePath());
                    info.put("fileSize", String.valueOf(file.length()));
                    info.put("lastModified", String.valueOf(file.lastModified()));
                    
                    // Extract timestamp from filename if it follows the new naming convention
                    String fileName = file.getName();
                    if (fileName.contains(".") && fileName.endsWith(".remise")) {
                        String[] parts = fileName.split("\\.");
                        if (parts.length >= 2) {
                            String timestamp = parts[parts.length - 2];
                            info.put("timestamp", timestamp);
                        }
                    }
                    
                    remises.add(info);
                }
            }
        }
        
        // Sort by filename or timestamp (most recent first)
        remises.sort((a, b) -> b.get("fileName").compareTo(a.get("fileName")));
        
        return ResponseEntity.ok(remises);
    }

    @GetMapping("/api/remises/file")
    public ResponseEntity<String> getRemiseFile(@RequestParam("path") String path) throws IOException {
        return ResponseEntity.ok(Files.readString(Paths.get(path)));
    }

    @PostMapping("/api/job/start")
    public ResponseEntity<Map<String, Object>> startJob(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String remisesDir = request.get("remisesDir");
            long currentTime = System.currentTimeMillis();
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", currentTime)
                    .addString("remisesDir", remisesDir)
                    .addString("outputDirectory", OUTPUT_DIR)
                    .toJobParameters();
            
            JobExecution jobExecution = jobLauncher.run(chequeJobThing, jobParameters);
            String jobId = jobExecution.getJobId().toString();
            
            // Initialize job status tracking
            JobStatusInfo jobInfo = new JobStatusInfo();
            jobInfo.jobExecution = jobExecution;
            jobInfo.remisesDir = remisesDir;
            jobInfo.startTime = System.currentTimeMillis();
            jobStatusMap.put(jobId, jobInfo);
            
            // Start UAP monitoring in a separate thread
            startUAPMonitoring(jobId);
            
            response.put("success", true);
            response.put("jobId", jobId);
            response.put("message", "Job started successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Job failed to start: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/job/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        Map<String, Object> response = new HashMap<>();
        
        JobStatusInfo jobInfo = jobStatusMap.get(jobId);
        if (jobInfo == null) {
            response.put("error", "Job not found");
            return ResponseEntity.notFound().build();
        }
        
        try {
            JobExecution jobExecution = jobRepository.getLastJobExecution(
                chequeJobThing.getName(), 
                jobInfo.jobExecution.getJobParameters()
            );
            
            if (jobExecution != null) {
                // Check DB Step (first step)
                StepExecution dbStep = jobExecution.getStepExecutions().stream()
                    .filter(step -> "dbStep".equals(step.getStepName()))
                    .findFirst()
                    .orElse(null);
                
                if (dbStep != null) {
                    response.put("dbStepStatus", dbStep.getStatus().toString());
                    if (dbStep.getStatus() == BatchStatus.COMPLETED) {
                        Map<String, Object> dbDetails = new HashMap<>();
                        dbDetails.put("recordsProcessed", dbStep.getReadCount());
                        dbDetails.put("filesMarked", "N/A"); // This would be tracked separately
                        response.put("dbStepDetails", dbDetails);
                    } else if (dbStep.getStatus() == BatchStatus.FAILED) {
                        response.put("dbStepError", getStepErrorMessage(dbStep));
                    }
                }
                
                // Check File Step (third step)
                StepExecution fileStep = jobExecution.getStepExecutions().stream()
                    .filter(step -> "fileStep".equals(step.getStepName()))
                    .findFirst()
                    .orElse(null);
                
                if (fileStep != null) {
                    response.put("fileStepStatus", fileStep.getStatus().toString());
                    if (fileStep.getStatus() == BatchStatus.COMPLETED) {
                        Map<String, Object> fileDetails = new HashMap<>();
                        // Count generated files
                        File outputDir = new File(OUTPUT_DIR);
                        File[] lotFiles = outputDir.listFiles((dir, name) -> name.endsWith(".LOT"));
                        File[] ordFiles = outputDir.listFiles((dir, name) -> name.endsWith(".ORD"));
                        
                        List<String> lotFileNames = new ArrayList<>();
                        List<String> ordFileNames = new ArrayList<>();
                        
                        if (lotFiles != null) {
                            for (File f : lotFiles) {
                                lotFileNames.add(f.getName());
                            }
                        }
                        if (ordFiles != null) {
                            for (File f : ordFiles) {
                                ordFileNames.add(f.getName());
                            }
                        }
                        
                        fileDetails.put("lotFiles", lotFileNames);
                        fileDetails.put("ordFiles", ordFileNames);
                        fileDetails.put("outputDir", OUTPUT_DIR);
                        response.put("fileStepDetails", fileDetails);
                        
                        // Update UAP monitoring info
                        jobInfo.ordFiles = ordFileNames;
                    } else if (fileStep.getStatus() == BatchStatus.FAILED) {
                        response.put("fileStepError", getStepErrorMessage(fileStep));
                    }
                }
            }
            
            // Check UAP Status
            if (jobInfo.uapStatus != null) {
                response.put("uapStatus", jobInfo.uapStatus);
                if (jobInfo.uapDetails != null) {
                    response.put("uapDetails", jobInfo.uapDetails);
                }
                if (jobInfo.uapError != null) {
                    response.put("uapError", jobInfo.uapError);
                }
            }
            
        } catch (Exception e) {
            response.put("error", "Failed to get job status: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    private String getStepErrorMessage(StepExecution stepExecution) {
        List<Throwable> failureExceptions = stepExecution.getFailureExceptions();
        if (!failureExceptions.isEmpty()) {
            return failureExceptions.get(0).getMessage();
        }
        return "Unknown error occurred";
    }

    private void startUAPMonitoring(String jobId) {
        Thread uapMonitorThread = new Thread(() -> {
            JobStatusInfo jobInfo = jobStatusMap.get(jobId);
            if (jobInfo == null) return;
            
            // Wait for job to complete first
            while (jobInfo.ordFiles == null || jobInfo.ordFiles.isEmpty()) {
                try {
                    Thread.sleep(5000);
                    if (System.currentTimeMillis() - jobInfo.startTime > 300000) { // 5 minutes timeout
                        jobInfo.uapStatus = "FAILED";
                        jobInfo.uapError = "Job did not complete within expected timeframe";
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            // Now monitor for UAP response
            long uapStartTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - uapStartTime < 120000) { // 2 minutes timeout for UAP
                try {
                    // Check if new ORD files appeared (UAP response)
                    File ordDir = new File(ORD_DIR);
                    File[] newOrdFiles = ordDir.listFiles((dir, name) -> name.endsWith(".ORD"));
                    
                    if (newOrdFiles != null && newOrdFiles.length > 0) {
                        // UAP responded, generate CRL files
                        List<String> crlFiles = generateCRLFiles(newOrdFiles);
                        
                        jobInfo.uapStatus = "COMPLETED";
                        jobInfo.uapDetails = new HashMap<>();
                        jobInfo.uapDetails.put("crlFiles", crlFiles);
                        return;
                    }
                    
                    Thread.sleep(5000); // Check every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            // Timeout reached
            jobInfo.uapStatus = "FAILED";
            jobInfo.uapError = "UAP did not respond within 2 minutes";
        });
        
        uapMonitorThread.setDaemon(true);
        uapMonitorThread.start();
    }

    private List<String> generateCRLFiles(File[] ordFiles) {
        List<String> crlFiles = new ArrayList<>();
        
        for (File ordFile : ordFiles) {
            try {
                // Generate corresponding CRL file
                String ordFileName = ordFile.getName();
                String crlFileName = ordFileName.replace(".ORD", ".CRL");
                File crlFile = new File(CRL_DIR, crlFileName);
                
                // Create empty CRL file (as requested)
                Files.createFile(crlFile.toPath());
                crlFiles.add(crlFileName);
                
            } catch (IOException e) {
                System.err.println("Failed to create CRL file for " + ordFile.getName() + ": " + e.getMessage());
            }
        }
        
        return crlFiles;
    }

    // Legacy endpoint for compatibility
    @PostMapping("/runJob")
    public String runJobLegacy(@RequestBody Map<String, String> request) {
        // Redirect to monitoring page
        return "redirect:/monitoring.html?remisesDir=" + request.get("remisesDir");
    }

    // Serve the monitoring page
    @GetMapping("/monitoring")
    public String monitoringPage() {
        return "monitoring";
    }

    @PostMapping("/api/job/start-cra")
    public ResponseEntity<Map<String, Object>> startCraJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            
            JobExecution execution = jobLauncher.run(craJob, jobParameters);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("jobId", execution.getId());
            response.put("message", "CRA job started successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to start CRA job: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Inner class to track job status
    private static class JobStatusInfo {
        JobExecution jobExecution;
        String remisesDir;
        long startTime;
        List<String> ordFiles;
        String uapStatus;
        Map<String, Object> uapDetails;
        String uapError;
    }
}