package com.akira.educacion;

import com.akira.educacion.support.TestFlywayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestFlywayConfig.class)
class EducacionApplicationTests {

	@Test
	void contextLoads() {
	}

}
