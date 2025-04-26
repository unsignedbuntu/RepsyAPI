package com.repsy.repsy_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "storage.strategy=filesystem")
class RepsyApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
