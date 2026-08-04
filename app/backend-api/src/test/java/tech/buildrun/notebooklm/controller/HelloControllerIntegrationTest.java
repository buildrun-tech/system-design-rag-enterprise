package tech.buildrun.notebooklm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.security.TestJwtDecoderConfig;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class HelloControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greetsUsingUsernameClaim() throws Exception {
        String sub = "hello-" + UUID.randomUUID();

        mockMvc.perform(get("/hello").header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, " + sub + "!"));
    }
}
