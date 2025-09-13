package com.repsy.repsy_api.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.stream.Stream;

public interface StorageService {

    /**
     * Initializes the storage.
     * For example, creates the root storage directory if it doesn't exist.
     */
    void init();

    /**
     * Stores a file uploaded via MultipartFile.
     *
     * @param file The file to store.
     * @param destinationPath The relative path within the storage where the file should be saved.
     */
    void store(MultipartFile file, Path destinationPath);

    /**
     * Loads all files within the storage.
     *
     * @return A Stream of Paths representing the files.
     */
    Stream<Path> loadAll();

    /**
     * Loads a file as a Path object.
     *
     * @param filename The relative path to the file.
     * @return The Path object for the file.
     */
    Path load(String filename);

    /**
     * Loads a file as a Spring Resource.
     *
     * @param filename The relative path to the file.
     * @return The Resource object for the file.
     */
    Resource loadAsResource(String filename);

    /**
     * Deletes all files managed by the storage service.
     * Use with caution!
     */
    void deleteAll();

    /**
     * Deletes a specific file.
     * @param filename The relative path to the file to delete.
     */
    void delete(String filename);

} 