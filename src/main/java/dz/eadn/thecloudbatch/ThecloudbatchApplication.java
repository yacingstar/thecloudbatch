package dz.eadn.thecloudbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;


@SpringBootApplication
@EnableTask
public class ThecloudbatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThecloudbatchApplication.class, args);
        System.out.println("ThecloudbatchApplication started successfully.");
    }


}
