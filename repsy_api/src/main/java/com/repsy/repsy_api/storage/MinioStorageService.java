package com.repsy.repsy_api.storage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

// @Service - REMOVED, managed by StorageConfiguration
// Only activate this bean if storage.strategy is 'minio' - REMOVED, managed by StorageConfiguration
// @ConditionalOnProperty(name = "storage.strategy", havingValue = "minio") // Managed in StorageConfiguration
public class MinioStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);

    private final StorageProperties properties;
    private final MinioClient minioClient;
    private final String bucketName;

    @Autowired
    public MinioStorageService(StorageProperties properties) {
        this.properties = properties;
        this.bucketName = properties.getMinio().getBucketName();
        this.minioClient = MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                .build();
         logger.info("MinioStorageService initialized for endpoint: {} and bucket: {}", properties.getMinio().getEndpoint(), bucketName);
    }

    @Override
    @PostConstruct // Run this method after the bean is created
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                logger.info("Minio bucket '{}' created successfully.", bucketName);
            } else {
                logger.info("Minio bucket '{}' already exists.", bucketName);
            }
        } catch (MinioException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            throw new StorageException("Could not initialize Minio storage, failed to check/create bucket: " + bucketName, e);
        }
    }

    @Override
    public void store(MultipartFile file, Path destinationPath) {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file.");
        }

        String destinationObjectName = destinationPath.toString().replace("\\", "/"); // Use forward slashes for object names

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(destinationObjectName)
                            .stream(inputStream, file.getSize(), -1) // size and partSize (-1 for auto)
                            .contentType(file.getContentType())
                            .build());
            logger.debug("Stored file {} to Minio bucket {} as {}", file.getOriginalFilename(), bucketName, destinationObjectName);
        } catch (MinioException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            throw new StorageException("Failed to store file " + destinationObjectName + " to Minio bucket " + bucketName, e);
        }
    }

    @Override
    public Stream<Path> loadAll() {
        // Note: Minio SDK listObjects returns an Iterable. Convert it to Stream.
        // This lists objects recursively by default.
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

        // Convert Iterable<Result<Item>> to Stream<Path>
        return StreamSupport.stream(results.spliterator(), false)
                .map(itemResult -> {
                    try {
                        // Convert object name (String) to Path
                        return Paths.get(itemResult.get().objectName());
                    } catch (Exception e) {
                        logger.error("Error getting item from Minio result", e);
                        throw new StorageException("Failed to read stored files from Minio", e);
                    }
                });
    }

    @Override
    public Path load(String filename) {
        // In Minio context, 'load' might just mean getting the object name as Path
        // Actual data loading happens in loadAsResource
        // Ensure consistent path separators
        return Paths.get(filename.replace("\\", "/"));
    }

    @Override
    public Resource loadAsResource(String filename) {
        String objectName = filename.replace("\\", "/");
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            // Wrap the InputStream in a Spring Resource
            // Note: This InputStreamResource is not reusable once read.
            return new InputStreamResource(stream) {
                // Override getFilename to provide the original filename
                @Override
                public String getFilename() {
                    return filename;
                }
                 // Add exists check based on trying to get metadata (optional but good practice)
                 @Override
                 public boolean exists() {
                    try {
                         minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
                         return true;
                     } catch (ErrorResponseException e) {
                        if (e.errorResponse().code().equals("NoSuchKey")) {
                             return false;
                        } else {
                             throw new StorageException("Failed to check existence for file " + filename, e);
                        }
                    } catch (Exception e) {
                         throw new StorageException("Failed to check existence for file " + filename, e);
                    }
                 }
            };
        } catch (ErrorResponseException e) {
             if (e.errorResponse().code().equals("NoSuchKey")) {
                 throw new StorageFileNotFoundException("Could not read file: " + filename + " from Minio bucket " + bucketName, e);
             } else {
                 throw new StorageException("Failed to read file " + filename + " from Minio bucket " + bucketName, e);
             }
        } catch (MinioException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            throw new StorageException("Failed to read file " + filename + " from Minio bucket " + bucketName, e);
        }
    }

    @Override
    public void deleteAll() {
        // Warning: Deletes all objects in the bucket!
        logger.warn("Attempting to delete all objects in Minio bucket: {}", bucketName);
        List<DeleteObject> objectsToDelete = new LinkedList<>();
        loadAll().forEach(path -> objectsToDelete.add(new DeleteObject(path.toString().replace("\\", "/"))));

        if (!objectsToDelete.isEmpty()) {
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder().bucket(bucketName).objects(objectsToDelete).build());

            // Check for errors during deletion
            results.forEach(errorResult -> {
                try {
                    DeleteError error = errorResult.get();
                    logger.error("Error deleting object {} from Minio: {}", error.objectName(), error.message());
                } catch (Exception e) {
                   logger.error("Error processing delete error result from Minio", e);
                   // Don't throw here to attempt deleting other objects
                }
            });
             logger.info("Finished deleting objects from Minio bucket: {}. Check logs for errors.", bucketName);
        } else {
             logger.info("No objects found to delete in Minio bucket: {}", bucketName);
        }
    }

    @Override
    public void delete(String filename) {
         String objectName = filename.replace("\\", "/");
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
            logger.debug("Deleted object {} from Minio bucket {}", objectName, bucketName);
        } catch (MinioException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            // Consider if not found should be an error or just ignored
             if (e instanceof ErrorResponseException && ((ErrorResponseException)e).errorResponse().code().equals("NoSuchKey")) {
                 logger.warn("Attempted to delete non-existent object: {}", objectName);
                 // Optionally throw StorageFileNotFoundException here if required
             } else {
                throw new StorageException("Failed to delete file " + objectName + " from Minio bucket " + bucketName, e);
             }
        }
    }
} 