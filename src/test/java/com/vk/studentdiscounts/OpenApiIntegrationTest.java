package com.vk.studentdiscounts;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiIntegrationTest extends ApiIntegrationTest {

    @Test
    void specificationAndSwaggerUiArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users/{userId}/block'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                // Публичные операции не требуют токен, остальные наследуют общее требование bearer.
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security", empty()));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
