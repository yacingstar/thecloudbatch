package dz.eadn.thecloudbatch.controller;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
public class IntegrateJobController {

    private static final String UPLOAD_BASE_DIR = "uploads/remises/";

    private JobLauncher jobLauncher;

    private Job chequeJobThing;
    
	public IntegrateJobController(JobLauncher jobLauncher, Job chequeJobThing) {
		this.jobLauncher = jobLauncher;
		this.chequeJobThing = chequeJobThing;
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
	        // Get all .remise files directly from the base directory
	        File[] remiseFiles = baseDir.listFiles((dir, name) -> name.endsWith(".remise"));
	        
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
	                        // Assuming the timestamp is the second-to-last part before .remise
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
