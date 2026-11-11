package br.com.fiap.PaperTrail;

import br.com.fiap.PaperTrail.support.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaperTrailApplicationTests extends JwtTestSupport {

	@Test
	void contextLoads() {
	}

}
