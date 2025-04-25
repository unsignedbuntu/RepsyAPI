package com.repsy.repsy_api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage") // prefix for properties in application.properties
public class StorageProperties {

    /**
     * Folder location for storing files
     */
    private String location = "upload-dir"; // Default location

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

} 