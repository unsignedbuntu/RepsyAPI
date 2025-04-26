# Repsy API - Imaginary Software Package System

## Summary

This project implements a REST API for an imaginary software package system called "Repsy", similar to how Maven or npm works but for a fictional "Repsy programming language". It allows deploying package versions (binary `.rep` file + `meta.json` metadata) and downloading specific package files.

The key feature is a pluggable storage layer using different strategies (Filesystem, Minio Object Storage) configured via application properties.

## Project Structure (Maven Modules)

This project follows a modular design:

*   `repsy-parent`: The parent POM defining common dependencies and properties for all modules.
*   `storage-api`: Defines the core interfaces (`StorageService`, `StorageProperties`) and exceptions for the storage layer. Other modules depend on this.
*   `storage-filesystem`: Contains the `FileSystemStorageService` implementation, storing packages on the local filesystem.
*   `storage-minio`: Contains the `MinioStorageService` implementation, storing packages in a Minio S3-compatible object storage bucket.
*   `repsy_api`: The main Spring Boot application containing the REST controllers, services (like `PackageService`), JPA entities, repositories, and the auto-configuration (`StorageAutoConfiguration`) to wire everything together. This is the runnable JAR.

## Configuration

Configuration is managed in `repsy_api/src/main/resources/application.properties`.

### Database (PostgreSQL)

Configure your PostgreSQL connection details:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/repsy_db
spring.datasource.username=your_db_user
spring.datasource.password=your_db_password
```

*   Ensure you have a PostgreSQL server running and the specified database (`repsy_db` by default) exists.
*   Update the username and password accordingly.
*   `spring.jpa.hibernate.ddl-auto=update` is set for development (updates schema automatically). Change to `validate` or `none` for production.

### Storage Strategy

The storage backend is selected using the `storage.strategy` property:

```properties
# Choose 'filesystem' or 'minio'
storage.strategy=filesystem
```

*   **`filesystem` (Default):** Stores packages on the local filesystem.
    *   `storage.location=upload-dir`: Specifies the directory (relative to the application's run location) where packages will be stored.
*   **`minio`:** Stores packages in a Minio bucket. Requires additional configuration:
    ```properties
    storage.strategy=minio

    storage.minio.endpoint=http://your-minio-server:9000
    storage.minio.access-key=your_minio_access_key
    storage.minio.secret-key=your_minio_secret_key
    storage.minio.bucket-name=repsy-packages # Or your preferred bucket name
    ```
    *   Ensure you have a Minio server running.
    *   Update the endpoint, keys, and bucket name. The specified bucket must exist or be creatable by the provided credentials.

## How to Build

Navigate to the project's root directory (`RepsyAPI`) in your terminal and run:

```bash
./mvnw clean install
```

This command will:

1.  Clean previous build artifacts.
2.  Compile all modules.
3.  Run tests (if any).
4.  Install the library modules (`storage-api`, `storage-filesystem`, `storage-minio`) into your local Maven repository (`.m2`).
5.  Package the main application (`repsy_api`) into a runnable JAR file in `repsy_api/target/`.

## How to Run

After a successful build, run the main application from the project's root directory:

```bash
./mvnw -pl repsy_api spring-boot:run
```

*   The `-pl repsy_api` flag tells Maven to run the `spring-boot:run` goal specifically on the `repsy_api` module.
*   The application will start, using the configuration from `repsy_api/src/main/resources/application.properties`.
*   By default, it will listen on port `8080`.

## API Endpoints

### 1. Deploy Package

*   **Method:** `POST`
*   **URL:** `/packages/{packageName}/{version}`
*   **Content-Type:** `multipart/form-data`
*   **Form Data:**
    *   `metaFile` (File): The `meta.json` file for the package.
    *   `repFile` (File): The binary `.rep` file for the package.
*   **Success Response:** `201 Created` (No body)
*   **Error Responses:**
    *   `409 Conflict`: If the package name and version already exist.
    *   `400 Bad Request`: If `metaFile` or `repFile` is empty, or if `meta.json` content is invalid (e.g., name/version mismatch, invalid JSON).

### 2. Download Package File

*   **Method:** `GET`
*   **URL:** `/packages/{packageName}/{version}/{fileName}`
    *   `fileName`: Can be the `.rep` file (e.g., `mypackage-1.0.0.rep`) or `meta.json`.
*   **Success Response:** `200 OK` with the requested file content and appropriate `Content-Type` header.
*   **Error Responses:**
    *   `404 Not Found`: If the package, version, or specific file does not exist in the configured storage.

---
*This README provides a basic overview. Further enhancements could include Docker support, more detailed error handling, security considerations, etc.* 