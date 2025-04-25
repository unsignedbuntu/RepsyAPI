package com.repsy.repsy_api.packages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repsy.repsy_api.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * @throws StorageException if there's an error storing the files.
     */
    @Transactional // Ensure atomicity: either all succeed (DB + file storage) or all fail
    public void deployPackage(String packageName, String version, MultipartFile repFile, MultipartFile metaFile) {
        logger.info("Attempting to deploy package: {} version: {}", packageName, version);

        // 1. Check if package version already exists
        Optional<PackageMetadata> existingPackage = packageRepository.findByNameAndVersion(packageName, version);
        if (existingPackage.isPresent()) {
            logger.warn("Deployment failed: Package {} version {} already exists.", packageName, version);
            throw new PackageAlreadyExistsException("Package '" + packageName + "' version '" + version + "' already exists.");
        }

        // 2. Parse and validate meta.json
        PackageMetadata metadata;
        try (InputStream metaInputStream = metaFile.getInputStream()) {
            // Attempt to parse JSON into our PackageMetadata entity
            // Note: ObjectMapper will only map fields present in both JSON and the class.
            // We might need a dedicated DTO for meta.json parsing for better validation.
            metadata = objectMapper.readValue(metaInputStream, PackageMetadata.class);

            // Basic validation: Check if name and version from URL match meta.json content
            if (!packageName.equals(metadata.getName()) || !version.equals(metadata.getVersion())) {
                logger.error("Metadata mismatch: URL ({}/{}) vs meta.json ({}/{})",
                        packageName, version, metadata.getName(), metadata.getVersion());
                throw new InvalidMetadataException("Package name or version in meta.json does not match the deployment URL.");
            }
             // Store the raw JSON string as well
             metaFile.getInputStream().reset(); // Reset stream if needed, or read again
             metadata.setDependenciesJson(new String(metaFile.getInputStream().readAllBytes()));

        } catch (IOException e) {
             logger.error("Failed to read or parse meta.json for {}/{}", packageName, version, e);
            throw new InvalidMetadataException("Failed to read or parse meta.json.", e);
        } catch (Exception e) { // Catch potential JSON parsing errors
            logger.error("Invalid JSON format in meta.json for {}/{}", packageName, version, e);
            throw new InvalidMetadataException("Invalid JSON format in meta.json.", e);
        }

        // 3. Store the files using StorageService
        // Construct paths like: {packageName}/{version}/{packageName}-{version}.rep
        // and {packageName}/{version}/meta.json
        Path packageRootPath = Paths.get(packageName, version);
        Path repFilePath = packageRootPath.resolve(packageName + "-" + version + ".rep");
        Path metaFilePath = packageRootPath.resolve("meta.json");

        try {
            logger.debug("Storing .rep file to: {}", repFilePath);
            storageService.store(repFile, repFilePath);
            logger.debug("Storing meta.json file to: {}", metaFilePath);
            storageService.store(metaFile, metaFilePath); // Store meta.json again for direct download later
        } catch (Exception e) { // Catch StorageException and others
            // Important: If file storage fails after DB save starts (though @Transactional helps),
            // we might need cleanup logic, but @Transactional should roll back the DB save.
            logger.error("Storage failed during deployment of {}/{}. DB changes will be rolled back.", packageName, version, e);
            throw new StorageException("Failed to store package files.", e);
        }

        // 4. Save metadata to the database
        // createdAt is set automatically in the entity
        try {
            logger.debug("Saving metadata to database for {}/{}", packageName, version);
            packageRepository.save(metadata);
        } catch (Exception e) {
            // Catch DataIntegrityViolationException etc.
            // This might happen if another request deployed the same package concurrently
            // after our initial check, despite the unique constraint.
            logger.error("Database save failed during deployment of {}/{}. Files might have been stored but DB transaction will be rolled back.", packageName, version, e);
            // Re-throw a more specific exception potentially
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

    public static class StorageException extends RuntimeException {
            public StorageException(String message) {
                super(message);
            }

            public StorageException(String message, Throwable cause) {
                super(message, cause);
            }
     }

     public static class PackageNotFoundException extends RuntimeException {
         public PackageNotFoundException(String message) {
             super(message);
         }
     }

    // --- Add methods for download etc. later ---
    // public Resource downloadPackageFile(String packageName, String version, String filename) { ... }

} 