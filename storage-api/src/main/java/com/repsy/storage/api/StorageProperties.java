package com.repsy.storage.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("storage")
public class StorageProperties {

    /**
     * Strategy to use for storage. Can be 'filesystem' or 'minio'.
     */
    private String strategy = "filesystem";

    /**
     * Base location for storing files when using the filesystem strategy.
     * Relative paths are resolved against the application's working directory.
     */
    private String location = "upload-dir";

    /**
     * Configuration specific to Minio object storage.
     */
    private final MinioProperties minio = new MinioProperties();

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public MinioProperties getMinio() {
        return minio;
    }

    public static class MinioProperties {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucketName = "repsy-packages";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }
    }
} 