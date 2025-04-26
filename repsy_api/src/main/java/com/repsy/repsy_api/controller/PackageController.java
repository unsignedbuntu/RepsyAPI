import com.repsy.repsy_api.packages.PackageService;
import com.repsy.repsy_api.storage.StorageService;
import com.repsy.repsy_api.storage.StorageFileNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class PackageController {

    private final PackageService packageService;
    private final StorageService storageService;

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

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (StorageFileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

} 