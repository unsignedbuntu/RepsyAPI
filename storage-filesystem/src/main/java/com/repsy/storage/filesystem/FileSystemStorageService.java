package com.repsy.storage.filesystem;

import com.repsy.storage.api.StorageException;
import com.repsy.storage.api.StorageFileNotFoundException;
import com.repsy.storage.api.StorageProperties;
import com.repsy.storage.api.StorageService;

import jakarta.annotation.PostConstruct;
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

// @Service - REMOVED, managed by StorageAutoConfiguration
// @ConditionalOnProperty(name = "storage.strategy", havingValue = "filesystem", matchIfMissing = true) // Managed in StorageAutoConfiguration
public class FileSystemStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemStorageService.class);

    private final Path rootLocation;
    private final StorageProperties properties;

    @Autowired
    public FileSystemStorageService(StorageProperties properties) {
        this.properties = properties;
        if(properties.getLocation().trim().isEmpty()){
            throw new StorageException("File upload location cannot be empty.");
        }
        this.rootLocation = Paths.get(properties.getLocation());
        logger.info("FileSystemStorageService initialized with root location: {}", this.rootLocation.toAbsolutePath());
    }

    @Override
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            logger.info("Root storage directory created/ensured at: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage at " + rootLocation.toAbsolutePath(), e);
        }
    }

    @Override
    public void store(MultipartFile file, Path destinationPath) {
        logger.info("Attempting to store file.");
        logger.info("Root location: {}", rootLocation.toAbsolutePath());
        logger.info("Provided destination path: {}", destinationPath);

        try {
            // Remove the commented-out empty file check
            // /* 
            // if (file.isEmpty()) {
            //     throw new StorageException("Failed to store empty file.");
            // }
            // */

            // Resolve the destination path against the root location
            Path absoluteDestinationFile = this.rootLocation.resolve(destinationPath).normalize().toAbsolutePath();
            logger.info("Calculated absolute destination file: {}", absoluteDestinationFile);

            // Security check: Ensure the destination is within the root location
            if (!absoluteDestinationFile.startsWith(this.rootLocation.toAbsolutePath())) {
                throw new StorageException("Cannot store file outside current directory: " + destinationPath);
            }

            // Create parent directories if they don't exist
            Path parentDir = absoluteDestinationFile.getParent();
             logger.info("Calculated parent directory: {}", parentDir);
            if (!Files.exists(parentDir)) {
                 logger.info("Creating parent directories if needed: {}", parentDir);
                try {
                    Files.createDirectories(parentDir);
                     logger.info("Parent directories should exist now.");
                } catch (IOException e) {
                    throw new StorageException("Could not create parent directories for " + absoluteDestinationFile, e);
                }
            }

            // Copy the file
             logger.info("Copying input stream to: {}", absoluteDestinationFile);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, absoluteDestinationFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("File copied successfully to: {}", absoluteDestinationFile);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to store file " + destinationPath, e);
        }
    }

    @Override
    public Stream<Path> loadAll() {
        try {
            return Files.walk(this.rootLocation, 1) // Only walk top level
                    .filter(path -> !path.equals(this.rootLocation))
                    .map(this.rootLocation::relativize);
        } catch (IOException e) {
            throw new StorageException("Failed to read stored files", e);
        }
    }

    @Override
    public Path load(String filename) {
        return rootLocation.resolve(filename);
    }

    @Override
    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            }
            else {
                 logger.warn("Could not read file: {}, resolved to: {}", filename, file.toAbsolutePath());
                throw new StorageFileNotFoundException("Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
             logger.error("MalformedURLException for file: {}, resolved to: {}", filename, rootLocation.resolve(filename).toAbsolutePath(), e);
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    @Override
    public void deleteAll() {
         logger.warn("Deleting all files in storage directory: {}", rootLocation.toAbsolutePath());
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
         // Re-initialize after deleting
         init();
    }

    @Override
    public void delete(String filename) {
        try {
            Path fileToDelete = load(filename).normalize();
             // Security check
             if (!fileToDelete.startsWith(this.rootLocation.toAbsolutePath())) {
                 logger.warn("Attempt to delete file outside root directory denied: {}", filename);
                 throw new StorageException("Cannot delete file outside current directory: " + filename);
             }
            boolean deleted = Files.deleteIfExists(fileToDelete);
             if (deleted) {
                 logger.info("Deleted file: {}", fileToDelete.toAbsolutePath());
             } else {
                 logger.warn("Attempted to delete non-existent file: {}", fileToDelete.toAbsolutePath());
                 // Optional: throw StorageFileNotFoundException if preferred
             }
        } catch (IOException e) {
             logger.error("Failed to delete file: {}", filename, e);
            throw new StorageException("Failed to delete file: " + filename, e);
        }
    }
} 