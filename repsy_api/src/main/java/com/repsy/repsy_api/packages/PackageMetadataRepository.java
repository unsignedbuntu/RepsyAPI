package com.repsy.repsy_api.packages;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // Mark this interface as a Spring Data repository
public interface PackageMetadataRepository extends JpaRepository<PackageMetadata, Long> {

    /**
     * Finds a package by its unique name and version combination.
     *
     * @param name The name of the package.
     * @param version The version of the package.
     * @return An Optional containing the found PackageMetadata, or empty if not found.
     */
    Optional<PackageMetadata> findByNameAndVersion(String name, String version);

    // Spring Data JPA will automatically implement this method based on its name
} 