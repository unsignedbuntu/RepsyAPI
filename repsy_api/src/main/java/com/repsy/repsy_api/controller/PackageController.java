import com.repsy.repsy_api.storage.StorageFileNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import java.nio.file.Paths;
import java.nio.file.Path;

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
        packageService.storePackage(packageName, version, repFile, metaFile);
    }

    @GetMapping("/{packageName}/{version}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String packageName,
                                                 @PathVariable String version,
                                                 @PathVariable String fileName) {
        try {
            Path filePath = Paths.get(packageName, version, fileName);
            Resource resource = storageService.loadAsResource(filePath);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=""" + resource.getFilename() + """)
                    .body(resource);
        } catch (StorageFileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

} 