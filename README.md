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

**Note:** Both `filesystem` and `minio` storage strategies have been tested and confirmed to be working correctly for package deployment and download operations.

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

## Running with Docker (Recommended)

This project includes a `Dockerfile` for the main application (`repsy_api/Dockerfile`) and a `docker-compose.yml` file in the root directory to easily run the application along with its dependencies (PostgreSQL and Minio).

### Prerequisites

*   Docker and Docker Compose installed.

### How to Run with Docker Compose

1.  **Navigate to the project root directory (`RepsyAPI`) in your terminal.**
2.  **Build and start the services:**
    ```bash
    docker compose up --build
    ```
    *   The `--build` flag is needed the first time or if you make changes to the `repsy_api` code or `Dockerfile`.
    *   This command will:
        *   Build the Docker image for the `repsy-api` service using `repsy_api/Dockerfile`.
        *   Download the official images for PostgreSQL (`db` service) and Minio (`minio` service).
        *   Start all three containers.
        *   Show the combined logs in your terminal.
    *   To run in detached mode (in the background), use `docker compose up -d --build`.

3.  **(If using Minio) Create Minio Bucket:**
    *   If you intend to use the `minio` storage strategy, you need to create the bucket specified in `docker-compose.yml` (default: `repsy-packages`).
    *   Open your browser and go to the Minio console: `http://localhost:9001`.
    *   Log in with the credentials defined in `docker-compose.yml` (default: `minioadmin`/`minioadmin`).
    *   Create the bucket named `repsy-packages`.

4.  **Access the API:** The Repsy API will be available at `http://localhost:8080`.

### Configuration with Docker Compose

*   The `docker-compose.yml` file uses environment variables to configure the `repsy-api` service, overriding settings in `application.properties`.
*   Database and Minio hostnames are set to the service names (`db` and `minio`).
*   **Default Storage:** The default storage strategy when running with Docker Compose is `filesystem`. Files will be stored *inside* the `repsy-api` container at `/app/upload-dir-in-container`.
*   **Using Minio Strategy:** To run with the Minio strategy, set the `STORAGE_STRATEGY` environment variable when starting:
    ```bash
    STORAGE_STRATEGY=minio docker compose up --build
    ```

### Stopping the Services

*   If running in the foreground, press `Ctrl+C` in the terminal where `docker compose up` is running.
*   If running in detached mode, navigate to the project root and run:
    ```bash
    docker compose down
    ```
    This will stop and remove the containers. Add the `-v` flag (`docker compose down -v`) to also remove the data volumes (`postgres_data`, `minio_data`).

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