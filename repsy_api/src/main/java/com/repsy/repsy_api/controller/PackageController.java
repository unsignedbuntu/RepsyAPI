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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
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
    public ResponseEntity<Resource> downloadFile(@PathVariable String packageName,
                                                 @PathVariable String version,
                                                 @PathVariable String fileName) {
        try {
            Path filePath = Paths.get(packageName, version, fileName);
            Resource resource = storageService.loadAsResource(filePath.toString());

            String contentType = "application/octet-stream";
            try {
                Path resourcePath = resource.getFile().toPath();
                contentType = Files.probeContentType(resourcePath);
                if (contentType == null) {
                    if (fileName.endsWith(".json")) contentType = "application/json";
                    else if (fileName.endsWith(".rep")) contentType = "application/octet-stream";
                    else contentType = "application/octet-stream";
                }
            } catch (IOException e) {
                logger.warn("Could not determine content type for {}, falling back to octet-stream", resource.getFilename(), e);
            }
            logger.info("Serving file {} with content type {}", resource.getFilename(), contentType);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (StorageFileNotFoundException e) {
            logger.warn("Not found error during download of file {} for package {}/{}: {}", fileName, packageName, version, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find file: " + fileName + " for package " + packageName + " version " + version, e);
        } catch (Exception e) {
            logger.error("Internal server error during download of file {} for package {}/{}", fileName, packageName, version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

} 