package com.repsy.repsy_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // Keep commented out for now
import org.springframework.context.annotation.Bean;

@SpringBootApplication
// @EnableConfigurationProperties(StorageProperties.class) // This is now handled by StorageAutoConfiguration
public class RepsyApiApplication {

	private static final Logger logger = LoggerFactory.getLogger(RepsyApiApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(RepsyApiApplication.class, args);
	}

	// REMOVE the conflicting bean definitions from here
	/*
	@Bean
    StorageService fileSystemStorageService(StorageProperties properties) {
        logger.info(">>> Creating FileSystemStorageService bean unconditionally.");
        return new FileSystemStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.strategy", havingValue = "minio")
    StorageService minioStorageService(StorageProperties properties) {
         logger.info(">>> Creating MinioStorageService bean based on strategy: {}", properties.getStrategy());
        return new MinioStorageService(properties);
    }
	*/

	// Keep CommandLineRunner commented out for now
	/* @Bean
	CommandLineRunner init(StorageService storageService) {
		return (args) -> {
			storageService.init();
		};
	} */

}
