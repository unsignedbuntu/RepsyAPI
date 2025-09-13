package com.repsy.repsy_api.controller;

import com.repsy.repsy_api.packages.PackageService;
import com.repsy.storage.api.StorageService;
import com.repsy.storage.api.StorageFileNotFoundException;
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
import java.io.InputStream;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/packages")
public class PackageController {

    private final PackageService packageService;
    private final StorageService storageService;
    private static final Logger logger = LoggerFactory.getLogger(PackageController.class);

    @Autowired
    public PackageController(PackageService packageService, StorageService storageService) {
        this.packageService = packageService;
        this.storageService = storageService;
    }

    @PostMapping("/{packageName}/{version}")
    @ResponseStatus(HttpStatus.CREATED)
    public void uploadPackage(@PathVariable String packageName,
                              @PathVariable String version,
                              @RequestParam("repFile") MultipartFile repFile,
                              @RequestParam("metaFile") MultipartFile metaFile) {
        packageService.deployPackage(packageName, version, repFile, metaFile);
    }

    @GetMapping("/{packageName}/{version}/{fileName:.+}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String packageName,
                                                 @PathVariable String version,
                                                 @PathVariable String fileName) {
        try {
            Path filePath = Paths.get(packageName, version, fileName);
            Resource resource = storageService.loadAsResource(filePath.toString());

            // Determine content type based on filename extension
            String contentType;
            if (fileName.endsWith(".json")) {
                contentType = MediaType.APPLICATION_JSON_VALUE;
            } else if (fileName.endsWith(".rep")) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                logger.warn("Could not determine specific content type for {}, falling back to {}", fileName, contentType);
            }

            logger.info("Serving file {} with content type {}", resource.getFilename(), contentType);

            // Read resource content into byte array
            byte[] content;
            try (InputStream inputStream = resource.getInputStream()) {
                content = inputStream.readAllBytes();
            } catch (IOException e) {
                logger.error("Failed to read resource content for {}", resource.getFilename(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Return byte array directly in the response body
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(content.length)
                    .body(content);

        } catch (StorageFileNotFoundException e) {
            logger.warn("Not found error during download of file {} for package {}/{}: {}", fileName, packageName, version, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find file: " + fileName + " for package " + packageName + " version " + version, e);
        } catch (Exception e) {
            logger.error("Internal server error during download of file {} for package {}/{}", fileName, packageName, version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(PackageService.PackageAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handlePackageAlreadyExists(PackageService.PackageAlreadyExistsException ex) {
        logger.warn("Conflict: {}", ex.getMessage());
        Map<String, String> responseBody = Map.of(
                "error", "Conflict",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(responseBody, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PackageService.InvalidFileException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFile(PackageService.InvalidFileException ex) {
        logger.warn("Bad Request: {}", ex.getMessage());
        Map<String, String> responseBody = Map.of(
                "error", "Bad Request",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PackageService.InvalidMetadataException.class)
    public ResponseEntity<Map<String, String>> handleInvalidMetadata(PackageService.InvalidMetadataException ex) {
        logger.warn("Bad Request (Metadata): {}", ex.getMessage());
        Map<String, String> responseBody = Map.of(
                "error", "Bad Request",
                "message", "Invalid package metadata: " + ex.getMessage()
        );
        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
    }

} 