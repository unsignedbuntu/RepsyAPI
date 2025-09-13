package com.repsy.repsy_api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage") // prefix for properties in application.properties
public class StorageProperties {

    /**
     * Strategy to use: filesystem or minio
     */
    private String strategy = "filesystem"; // Default strategy

    /**
     * Folder location for storing files (used by filesystem strategy)
     */
    private String location = "upload-dir"; // Default location

    /**
     * Configuration specific to Minio
     */
    private final Minio minio = new Minio();

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

    public Minio getMinio() {
        return minio;
    }

    // Inner class to group Minio properties
    public static class Minio {
        /**
         * Minio server endpoint URL
         */
        private String endpoint;
        /**
         * Minio access key
         */
        private String accessKey;
        /**
         * Minio secret key
         */
        private String secretKey;
        /**
         * Minio bucket name to use
         */
        private String bucketName = "repsy-packages"; // Default bucket name

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