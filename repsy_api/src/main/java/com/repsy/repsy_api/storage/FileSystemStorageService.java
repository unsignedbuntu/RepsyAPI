package com.repsy.repsy_api.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

// Mark this class as a Spring Service - REMOVED, managed by StorageConfiguration
// Add conditional logic later to only enable this if file-system strategy is chosen - REMOVED, managed by StorageConfiguration
// @Service - REMOVED
// @ConditionalOnProperty(name = "storage.strategy", havingValue = "filesystem", matchIfMissing = true) // Managed in StorageConfiguration
public class FileSystemStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemStorageService.class);
    private final Path rootLocation;

    @Autowired
    public FileSystemStorageService(StorageProperties properties) {
        // Construct the root path from the configured location property
        this.rootLocation = Paths.get(properties.getLocation());
    }

    @Override
    public void init() {
        try {
            // Create the root directory if it doesn't exist
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }

    @Override
    public void store(MultipartFile file, Path destinationPath) {
        try {
            // Construct the absolute destination path
            Path absoluteDestinationFile = this.rootLocation.resolve(destinationPath).normalize().toAbsolutePath();
            Path parentDir = absoluteDestinationFile.getParent();

            // --- DEBUG LOGGING START ---
            logger.info("Attempting to store file.");
            logger.info("Root location: {}", this.rootLocation.toAbsolutePath());
            logger.info("Provided destination path: {}", destinationPath);
            logger.info("Calculated absolute destination file: {}", absoluteDestinationFile);
            logger.info("Calculated parent directory: {}", parentDir);
            // --- DEBUG LOGGING END ---

            // Security check: ensure the destination is within the root location
            if (!parentDir.startsWith(this.rootLocation.toAbsolutePath())) {
                // Log the check failure for debugging
                logger.error("Security check failed: Parent directory {} is not within root location {}", parentDir, this.rootLocation.toAbsolutePath());
                throw new StorageException("Cannot store file outside current directory.");
            }

            // Create parent directories if they don't exist
            logger.info("Creating parent directories if needed: {}", parentDir);
            Files.createDirectories(parentDir);
            logger.info("Parent directories should exist now.");

            // Copy the file's input stream to the destination path, replacing existing files
            try (InputStream inputStream = file.getInputStream()) {
                logger.info("Copying input stream to: {}", absoluteDestinationFile);
                Files.copy(inputStream, absoluteDestinationFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("File copied successfully to: {}", absoluteDestinationFile);
            }
        } catch (IOException e) {
            // Log the exception before throwing
            logger.error("IOException during file storage to {}: {}", destinationPath, e.getMessage(), e);
            throw new StorageException("Failed to store file.", e);
        } catch (Exception e) { // Catch any other unexpected exception
             logger.error("Unexpected exception during file storage to {}: {}", destinationPath, e.getMessage(), e);
            throw new StorageException("Unexpected error storing file.", e);
        }
    }


    @Override
    public Stream<Path> loadAll() {
       try {
            // Walk through the root location, find files (not directories), and return their relative paths
            return Files.walk(this.rootLocation, 1) // Only walk top level for now, adjust depth if needed
                    .filter(path -> !path.equals(this.rootLocation))
                    .map(this.rootLocation::relativize);
        } catch (IOException e) {
            throw new StorageException("Failed to read stored files", e);
        }
    }

    @Override
    public Path load(String filename) {
        // Resolve the filename against the root location
        return rootLocation.resolve(filename);
    }

    @Override
    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new StorageFileNotFoundException("Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    @Override
    public void deleteAll() {
        // Recursively delete the contents of the root location
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }

     @Override
    public void delete(String filename) {
        try {
            Path fileToDelete = load(filename).normalize().toAbsolutePath();
             // Security check: ensure the file is within the root location
            if (!fileToDelete.startsWith(this.rootLocation.toAbsolutePath())) {
                 throw new StorageException("Cannot delete file outside current directory.");
            }
            Files.deleteIfExists(fileToDelete);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + filename, e);
        }
    }


    // Helper exception classes removed, moved to separate files.

} 