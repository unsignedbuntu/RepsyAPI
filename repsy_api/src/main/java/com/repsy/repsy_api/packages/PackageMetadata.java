package com.repsy.repsy_api.packages;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "packages", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "version"}) // Ensure name+version combination is unique
})
public class PackageMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "package_seq")
    @SequenceGenerator(name = "package_seq", sequenceName = "package_sequence", allocationSize = 1)
    private Long id;

    @NotBlank(message = "Package name cannot be blank")
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Version cannot be blank")
    // Basic semantic versioning pattern (adjust regex as needed for complexity)
    @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$", message = "Version must follow semantic versioning (e.g., 1.0.0)")
    @Column(nullable = false)
    private String version;

    private String author;

    // Store the dependencies JSON as a String (or potentially JSON/JSONB if DB supports)
    @Column(columnDefinition = "TEXT") // Use TEXT for potentially long JSON string
    @JdbcTypeCode(SqlTypes.JSON) // Hint for Hibernate/JDBC driver
    private String dependenciesJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now(); // Record creation timestamp

    // --- Getters and Setters (or use Lombok @Data) ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDependenciesJson() {
        return dependenciesJson;
    }

    public void setDependenciesJson(String dependenciesJson) {
        this.dependenciesJson = dependenciesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // --- equals, hashCode, toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageMetadata that = (PackageMetadata) o;
        // Use name and version for equality
        return Objects.equals(name, that.name) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        // Use name and version for hash code
        return Objects.hash(name, version);
    }

    @Override
    public String toString() {
        return "PackageMetadata{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", author='" + author + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
} 