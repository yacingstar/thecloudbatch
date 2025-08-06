package dz.eadn.thecloudbatch;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileMarkingTasklet implements Tasklet {
    
    private final String remisesDir;
    
    public FileMarkingTasklet(String remisesDir) {
        this.remisesDir = remisesDir;
    }
    
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        File dir = new File(remisesDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".remise") && !name.endsWith(".remise.DONE"));
        
        if (files != null) {
            for (File file : files) {
                String newFileName = file.getName() + ".DONE";
                File newFile = new File(file.getParent(), newFileName);
                
                // Rename the file to add .DONE extension
                Files.move(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                System.out.println("Marked file as DONE: " + newFile.getName());
            }
        }
        
        return RepeatStatus.FINISHED;
    }
}