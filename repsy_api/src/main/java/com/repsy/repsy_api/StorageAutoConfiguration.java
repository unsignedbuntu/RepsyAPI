package com.repsy.repsy_api;

// Updated imports from new storage modules
import com.repsy.storage.api.StorageProperties;
import com.repsy.storage.api.StorageService;
import com.repsy.storage.filesystem.FileSystemStorageService;
import com.repsy.storage.minio.MinioStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    @ConditionalOnMissingBean // Add this to prevent conflicts if defined elsewhere
    StorageService fileSystemStorageService(StorageProperties properties) {
        logger.info("AutoConfig: Creating FileSystemStorageService bean (strategy: {}, location: {})", properties.getStrategy(), properties.getLocation());
        return new FileSystemStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.strategy", havingValue = "minio")
    @ConditionalOnMissingBean // Add this to prevent conflicts if defined elsewhere
    StorageService minioStorageService(StorageProperties properties) {
        logger.info("AutoConfig: Creating MinioStorageService bean (strategy: {}, endpoint: {}, bucket: {})",
                properties.getStrategy(), properties.getMinio().getEndpoint(), properties.getMinio().getBucketName());
        // Validate Minio properties before creating the bean
        if (properties.getMinio() == null || properties.getMinio().getEndpoint() == null || properties.getMinio().getBucketName() == null) {
            logger.error("Minio configuration is incomplete. Please check application properties (storage.minio.endpoint, storage.minio.bucket-name)");
            throw new IllegalStateException("Minio configuration is incomplete.");
        }
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