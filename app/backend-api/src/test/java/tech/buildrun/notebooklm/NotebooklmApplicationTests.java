package tech.buildrun.notebooklm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.mockito.Mockito.mockStatic;

@SpringBootTest
class NotebooklmApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void mainStartsSpringApplication() {
		try (var mocked = mockStatic(SpringApplication.class)) {
			NotebooklmApplication.main(new String[] {});

			mocked.verify(() -> SpringApplication.run(NotebooklmApplication.class, new String[] {}));
		}
	}

}
