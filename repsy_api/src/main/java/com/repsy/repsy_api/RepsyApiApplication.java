package com.repsy.repsy_api;

import com.repsy.repsy_api.storage.FileSystemStorageService;
import com.repsy.repsy_api.storage.MinioStorageService;
import com.repsy.repsy_api.storage.StorageProperties;
import com.repsy.repsy_api.storage.StorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
@ComponentScan(basePackages = "com.repsy.repsy_api")
public class RepsyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepsyApiApplication.class, args);
	}

	// Moved StorageService Bean definitions here
	@Bean
    @ConditionalOnProperty(name = "storage.strategy", havingValue = "filesystem", matchIfMissing = true)
    StorageService fileSystemStorageService(StorageProperties properties) {
        // Need to inject properties here too
        return new FileSystemStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.strategy", havingValue = "minio")
    StorageService minioStorageService(StorageProperties properties) {
         // Need to inject properties here too
        return new MinioStorageService(properties);
    }

	// We can add a CommandLineRunner bean later to initialize the storage on startup
	/* @Bean
	CommandLineRunner init(StorageService storageService) {
		return (args) -> {
			// Initialize storage on startup (create root directory/bucket)
			try {
                storageService.init();
            } catch (Exception e) {
                System.err.println("Failed to initialize storage: " + e.getMessage());
                // Decide if the application should fail to start here
            }
			// Optional: Clear storage on startup - Use with caution!
			// storageService.deleteAll();
		};
	} */

}
