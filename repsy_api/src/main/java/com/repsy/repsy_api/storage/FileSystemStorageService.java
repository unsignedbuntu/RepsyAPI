package com.repsy.repsy_api.storage;

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

// Mark this class as a Spring Service
// Add conditional logic later to only enable this if file-system strategy is chosen
@Service
public class FileSystemStorageService implements StorageService {

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
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }
            // Construct the absolute destination path
            Path absoluteDestinationFile = this.rootLocation.resolve(destinationPath).normalize().toAbsolutePath();

            // Security check: ensure the destination is within the root location
            if (!absoluteDestinationFile.getParent().startsWith(this.rootLocation.toAbsolutePath())) {
                throw new StorageException("Cannot store file outside current directory.");
            }

            // Create parent directories if they don't exist
            Files.createDirectories(absoluteDestinationFile.getParent());

            // Copy the file's input stream to the destination path, replacing existing files
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, absoluteDestinationFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to store file.", e);
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


    // Helper exception classes (can be moved to separate files later if preferred)

    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class StorageFileNotFoundException extends StorageException {
        public StorageFileNotFoundException(String message) {
            super(message);
        }

        public StorageFileNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 