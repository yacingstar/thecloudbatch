package dz.eadn.thecloudbatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FileNameBuilder {
    private final List<String> nameParts = new ArrayList<>();
    private String extension;
    
    public FileNameBuilder() {}
    
    public FileNameBuilder addPart(String part) {
        if (part != null && !part.trim().isEmpty()) {
            nameParts.add(String.valueOf(part));
        }
        return this;
    }
    
    public FileNameBuilder addFormattedPart(String format, Object... args) {
        return addPart(String.format(format, args));
    }
    
    public FileNameBuilder addBeneficiaryBank(int bank) {
        return addFormattedPart("%03d", bank);
    }
    
    public FileNameBuilder addLotNumber(int lotNumber) {
        return addFormattedPart("%03d", lotNumber);
    }
    
    public FileNameBuilder addOperationType(int operationType) {
        return addFormattedPart("%03d", operationType);
    }
    
    public FileNameBuilder addTimestamp() {
        return addPart(new SimpleDateFormat("yyyyMMddHH").format(new Date()));
    }
    
    public FileNameBuilder addDate(String format) {
        return addPart(new SimpleDateFormat(format).format(new Date()));
    }
    
    public FileNameBuilder extension(String extension) {
        this.extension = extension;
        return this;
    }
    
    public String build() {
        if (nameParts.isEmpty()) {
            throw new IllegalStateException("No name parts added");
        }
        
        StringBuilder fileName = new StringBuilder();
        fileName.append(String.join(".", nameParts));
        
        if (extension != null && !extension.trim().isEmpty()) {
            fileName.append(".").append(extension.trim());
        }
        
        return fileName.toString();
    }
    
    public FileNameBuilder clear() {
        nameParts.clear();
        extension = null;
        return this;
    }
}

