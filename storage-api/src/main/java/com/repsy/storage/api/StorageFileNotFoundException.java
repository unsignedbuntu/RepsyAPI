package com.repsy.storage.api;

/**
 * Exception thrown when a requested file is not found in the storage.
 */
public class StorageFileNotFoundException extends StorageException {

	public StorageFileNotFoundException(String message) {
		super(message);
	}

	public StorageFileNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
} 