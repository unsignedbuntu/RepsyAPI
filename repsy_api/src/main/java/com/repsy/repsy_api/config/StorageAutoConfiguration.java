package com.repsy.repsy_api.config;

// Updated imports from new storage modules
import com.repsy.storage.api.StorageProperties;
import com.repsy.storage.api.StorageService;
import com.repsy.storage.filesystem.FileSystemStorageService;
import com.repsy.storage.minio.MinioStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class) // Enable properties defined in storage-api
public class StorageAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StorageAutoConfiguration.class);

    // Inject StorageProperties (now defined in storage-api)
    private final StorageProperties properties;

    public StorageAutoConfiguration(StorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnProperty(name = "storage.strategy", havingValue = "filesystem", matchIfMissing = true)
    StorageService fileSystemStorageService() {
        logger.info("Initializing FileSystemStorageService based on configuration.");
        // Return the implementation from storage-filesystem module
        return new FileSystemStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.strategy", havingValue = "minio")
    StorageService minioStorageService() {
        logger.info("Initializing MinioStorageService based on configuration.");
        // Return the implementation from storage-minio module
        return new MinioStorageService(properties);
    }

    // We might not need the init caller bean anymore if @PostConstruct is used
    // in the service implementations.
    /*
    @Bean
    InitializingBean initializeStorage(StorageService storageService) {
        return () -> {
            logger.info("Calling init() on selected StorageService implementation.");
            storageService.init();
        };
    }
    */
} 