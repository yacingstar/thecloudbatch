package dz.eadn.thecloudbatch.controller;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class IntegrateJob {

    private static final String UPLOAD_BASE_DIR = "uploads/remises/";

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job chequeJobThing;

    @PostMapping("/api/upload/remises")
    public ResponseEntity<Map<String, String>> uploadRemises(@RequestParam("files") MultipartFile[] files) {
        Map<String, String> response = new HashMap<>();
        
        try {
            // Create unique directory for this upload session
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String sessionDir = UPLOAD_BASE_DIR + "session_" + timestamp + "/";
            
            // Create directories if they don't exist
            Path uploadPath = Paths.get(sessionDir);
            Files.createDirectories(uploadPath);
            
            // Save each file
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String fileName = file.getOriginalFilename();
                    Path filePath = uploadPath.resolve(fileName);
                    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            
            // Return the absolute server directory path
            String absolutePath = uploadPath.toAbsolutePath().toString();
            response.put("directoryPath", absolutePath);
            response.put("status", "success");
            response.put("filesCount", String.valueOf(files.length));
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/runJob")
    public String runJob(@RequestBody Map<String, String> request) {
        try {
            String remisesDir = request.get("remisesDir");
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", System.currentTimeMillis())
                    .addString("remisesDir", remisesDir)
                    .toJobParameters();
            jobLauncher.run(chequeJobThing, jobParameters);
            return "Job started successfully";
        } catch (Exception e) {
            return "Job failed to start: " + e.getMessage();
        }
    }
}