package com.sporya.auth.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

  @Bean
  OpenAPI authServiceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Sporya - Auth Service")
                .description("Identité, authentification, rôles par club.")
                .version("v1"));
  }
}
