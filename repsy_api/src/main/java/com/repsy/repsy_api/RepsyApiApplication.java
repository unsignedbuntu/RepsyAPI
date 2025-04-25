package com.repsy.repsy_api;

import com.repsy.repsy_api.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class RepsyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepsyApiApplication.class, args);
	}

	// We can add a CommandLineRunner bean later to initialize the storage on startup
	// @Bean
	// CommandLineRunner init(StorageService storageService) {
	// 	return (args) -> {
	// 		storageService.deleteAll(); // Optional: Clear storage on startup
	// 		storageService.init();
	// 	};
	// }

}
