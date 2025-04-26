package com.repsy.repsy_api.packages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repsy.storage.api.StorageException;
import com.repsy.storage.api.StorageFileNotFoundException;
import com.repsy.storage.api.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Optional;

@Service
public class PackageService {

    private static final Logger logger = LoggerFactory.getLogger(PackageService.class);

    private final PackageMetadataRepository packageRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper; // For parsing meta.json

    @Autowired
    public PackageService(PackageMetadataRepository packageRepository, StorageService storageService, ObjectMapper objectMapper) {
        this.packageRepository = packageRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Deploys a new package version.
     *
     * @param packageName    The name of the package.
     * @param version        The version of the package.
     * @param repFile        The .rep binary file.
     * @param metaFile       The meta.json metadata file.
     * @throws PackageAlreadyExistsException if the package name and version combination already exists.
     * @throws InvalidMetadataException if meta.json is invalid or doesn't match expected fields.
     * @throws InvalidFileException if repFile or metaFile is empty.
     * @throws StorageException if there's an error storing the files.
     */
    @Transactional // Ensure atomicity: either all succeed (DB + file storage) or all fail
    public void deployPackage(String packageName, String version, MultipartFile repFile, MultipartFile metaFile)
            throws PackageAlreadyExistsException, InvalidMetadataException, InvalidFileException, StorageException {
        logger.info("Attempting to deploy package: {} version: {}", packageName, version);

        // --- Start: Added empty file checks ---
        if (repFile == null || repFile.isEmpty()) {
            logger.warn("Deployment failed: repFile is empty for {}/{}", packageName, version);
            throw new InvalidFileException("'repFile' cannot be empty.");
        }
        if (metaFile == null || metaFile.isEmpty()) {
            logger.warn("Deployment failed: metaFile is empty for {}/{}", packageName, version);
            throw new InvalidFileException("'metaFile' cannot be empty.");
        }
        // --- End: Added empty file checks ---

        // 1. Check if package version already exists
        Optional<PackageMetadata> existingPackage = packageRepository.findByNameAndVersion(packageName, version);
        if (existingPackage.isPresent()) {
            logger.warn("Deployment failed: Package {} version {} already exists.", packageName, version);
            throw new PackageAlreadyExistsException("Package '" + packageName + "' version '" + version + "' already exists.");
        }

        // 2. Parse and validate meta.json
        PackageMetadata metadata;
        String rawJsonDependencies;
        try (InputStream metaInputStream = metaFile.getInputStream()) {
            // Read all bytes first to allow parsing and storing raw JSON
            byte[] metaBytes = metaInputStream.readAllBytes();
            rawJsonDependencies = new String(metaBytes);
            metadata = objectMapper.readValue(metaBytes, PackageMetadata.class);

            // Basic validation: Check if name and version from URL match meta.json content
            if (!packageName.equals(metadata.getName()) || !version.equals(metadata.getVersion())) {
                logger.error("Metadata mismatch: URL ({}/{}) vs meta.json ({}/{})",
                        packageName, version, metadata.getName(), metadata.getVersion());
                throw new InvalidMetadataException("Package name or version in meta.json does not match the deployment URL.");
            }
            // Store the raw JSON string
            metadata.setDependenciesJson(rawJsonDependencies);

        } catch (IOException e) {
            logger.error("Failed to read meta.json for {}/{}", packageName, version, e);
            throw new InvalidMetadataException("Failed to read meta.json.", e);
        } catch (Exception e) { // Catch potential JSON parsing errors
            logger.error("Invalid JSON format in meta.json for {}/{}", packageName, version, e);
            throw new InvalidMetadataException("Invalid JSON format in meta.json.", e);
        }

        // 3. Store the files using StorageService
        Path packageRootPath = Paths.get(packageName, version);
        Path repFilePath = packageRootPath.resolve(packageName + "-" + version + ".rep");
        Path metaFilePath = packageRootPath.resolve("meta.json");

        try {
            logger.debug("Storing .rep file to: {}", repFilePath);
            storageService.store(repFile, repFilePath);
            logger.debug("Storing meta.json file to: {}", metaFilePath);
            // Re-use the already read metaBytes to avoid reading the file again
            storageService.store(new ByteArrayMultipartFile(metaFile.getName(), metaFile.getOriginalFilename(), metaFile.getContentType(), rawJsonDependencies.getBytes()), metaFilePath);
        } catch (StorageException e) { // Catch specific storage exception
            logger.error("Storage failed during deployment of {}/{}. DB changes will be rolled back.", packageName, version, e);
            throw e; // Re-throw the original StorageException
        } catch (Exception e) {
            logger.error("Unexpected storage error during deployment of {}/{}. DB changes will be rolled back.", packageName, version, e);
            throw new StorageException("Failed to store package files due to an unexpected error.", e);
        }

        // 4. Save metadata to the database
        try {
            logger.debug("Saving metadata to database for {}/{}", packageName, version);
            packageRepository.save(metadata);
        } catch (Exception e) {
            logger.error("Database save failed during deployment of {}/{}. Files might have been stored but DB transaction will be rolled back.", packageName, version, e);
            throw new RuntimeException("Failed to save package metadata to database.", e);
        }

        logger.info("Successfully deployed package: {} version: {}", packageName, version);
    }

    // --- Custom Exception Classes (can be moved to separate files) ---

    public static class PackageAlreadyExistsException extends RuntimeException {
        public PackageAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class InvalidMetadataException extends RuntimeException {
        public InvalidMetadataException(String message) {
            super(message);
        }
        public InvalidMetadataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PackageNotFoundException extends RuntimeException {
        public PackageNotFoundException(String message) {
            super(message);
        }
         public PackageNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InvalidFileException extends RuntimeException {
        public InvalidFileException(String message) {
            super(message);
        }
        public InvalidFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Loads a specific file (e.g., .rep or meta.json) for a given package and version.
     *
     * @param packageName The name of the package.
     * @param version     The version of the package.
     * @param filename    The name of the file to load (e.g., "mypackage-1.0.0.rep" or "meta.json").
     * @return A Spring Resource representing the file.
     * @throws PackageNotFoundException if the package or the specific file does not exist.
     * @throws StorageException         if there is an issue accessing the storage.
     */
    public Resource loadPackageResource(String packageName, String version, String filename)
            throws PackageNotFoundException, StorageException {
        logger.debug("Attempting to load resource {} for package {}/{}", filename, packageName, version);

        Path filePath = Paths.get(packageName, version, filename);
        String filePathString = filePath.toString().replace("\\", "/");

        try {
            Resource resource = storageService.loadAsResource(filePathString);
            if (resource.exists() && resource.isReadable()) {
                logger.info("Resource {} found for package {}/{}", filename, packageName, version);
                return resource;
            } else {
                logger.warn("Resource {} not found or not readable for package {}/{} at path {}", filename, packageName, version, filePathString);
                // Throw PackageNotFoundException which semantically fits better here than StorageFileNotFoundException
                throw new PackageNotFoundException("Could not find or read file: " + filename + " for package " + packageName + " version " + version);
            }
        } catch (StorageFileNotFoundException e) { // Catch specific exception from StorageService
            logger.warn("StorageFileNotFoundException for resource {} in package {}/{}: {}", filename, packageName, version, e.getMessage());
            throw new PackageNotFoundException("Could not find file: " + filename + " for package " + packageName + " version " + version, e);
        } catch (StorageException e) { // Catch other storage exceptions
            logger.error("Storage error loading resource {} for package {}/{}: {}", filename, packageName, version, e.getMessage(), e);
            throw e; // Re-throw the original StorageException
        } catch (Exception e) { // Catch unexpected errors
            logger.error("Unexpected error loading resource {} for package {}/{}: {}", filename, packageName, version, e.getMessage(), e);
            throw new StorageException("Could not load file: " + filename, e);
        }
    }

    // Helper class to re-store meta.json without re-reading the original MultipartFile stream
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() throws IOException { return content; }
        @Override public InputStream getInputStream() throws IOException { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(Path dest) throws IOException, IllegalStateException { Files.write(dest, content); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException { Files.write(dest.toPath(), content); }
    }
} 