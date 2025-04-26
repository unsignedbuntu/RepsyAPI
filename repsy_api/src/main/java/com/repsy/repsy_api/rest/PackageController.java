package com.repsy.repsy_api.rest;

import com.repsy.repsy_api.packages.PackageService;
import com.repsy.repsy_api.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
@RequestMapping("/packages") // Base path for package related endpoints
public class PackageController {

    private static final Logger logger = LoggerFactory.getLogger(PackageController.class);

    private final PackageService packageService;

    @Autowired
    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    /**
     * Handles the deployment of a new package version.
     * Accepts packageName and version from the path, and .rep and meta.json files as multipart data.
     */
    @PostMapping("/{packageName}/{version}")
    public ResponseEntity<String> deployPackage(
            @PathVariable String packageName,
            @PathVariable String version,
            @RequestParam("repFile") MultipartFile repFile,
            @RequestParam("metaFile") MultipartFile metaFile) {

        logger.info("Received deployment request for package: {} version: {}", packageName, version);

        // Basic validation for uploaded files
        if (metaFile == null || metaFile.isEmpty()) {
            logger.warn("Deployment request for {}/{} rejected: Missing or empty metaFile.", packageName, version);
            return ResponseEntity.badRequest().body("metaFile is required and cannot be empty.");
        }
        if (repFile == null) {
            logger.warn("Deployment request for {}/{} rejected: Missing repFile.", packageName, version);
            return ResponseEntity.badRequest().body("repFile is required.");
        }

        // Consider adding more specific file type/size checks if necessary

        try {
            packageService.deployPackage(packageName, version, repFile, metaFile);
            String successMessage = String.format("Package '%s' version '%s' deployed successfully.", packageName, version);
            logger.info(successMessage);
            return ResponseEntity.status(HttpStatus.CREATED).body(successMessage);
        } catch (PackageService.PackageAlreadyExistsException e) {
            logger.warn("Conflict during deployment of {}/{}: {}", packageName, version, e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (PackageService.InvalidMetadataException e) {
            logger.warn("Bad request during deployment of {}/{}: {}", packageName, version, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (StorageException e) {
            logger.error("Storage error during deployment of {}/{}: {}", packageName, version, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store package files.", e);
        } catch (Exception e) {
            // Catch-all for unexpected errors
            logger.error("Unexpected error during deployment of {}/{}: {}", packageName, version, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred during deployment.", e);
        }
    }

    // --- Add endpoints for download later ---
    // @GetMapping("/{packageName}/{version}/{fileName:.+})")
    // public ResponseEntity<Resource> downloadFile(...) { ... }

    /**
     * Handles the download of a specific package file (.rep or meta.json).
     */
    @GetMapping("/{packageName}/{version}/{fileName:.+}") // :.+ ensures filename with dots is captured
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String packageName,
            @PathVariable String version,
            @PathVariable String fileName) {

        logger.info("Received download request for file {} from package {}/{}", fileName, packageName, version);

        try {
            Resource resource = packageService.loadPackageResource(packageName, version, fileName);

            // Try to determine content type (optional, browser might guess)
            String contentType = null;
            try {
                // A simple way, might need improvement for more accuracy
                contentType = Files.probeContentType(Paths.get(resource.getFilename()));
            } catch (IOException ex) {
                logger.warn("Could not determine content type for file: {}", resource.getFilename(), ex);
            }
            // Default if probe fails
            if(contentType == null) {
                contentType = "application/octet-stream";
            }

            logger.info("Serving file {} with content type {}", resource.getFilename(), contentType);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    // Header to suggest download with the original filename
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (PackageService.PackageNotFoundException e) {
             logger.warn("Not found error during download of file {} for package {}/{}: {}", fileName, packageName, version, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (StorageException e) {
             logger.error("Storage error during download of file {} for package {}/{}: {}", fileName, packageName, version, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving file from storage.", e);
        } catch (Exception e) {
             logger.error("Unexpected error during download of file {} for package {}/{}: {}", fileName, packageName, version, e.getMessage(), e);
             throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred during file download.", e);
        }
    }

    // Consider adding a @ControllerAdvice for more centralized exception handling later.

} 