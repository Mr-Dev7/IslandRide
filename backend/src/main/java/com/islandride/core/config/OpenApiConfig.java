package com.islandride.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IslandRide Platform REST API")
                        .version("1.0.0")
                        .description("Multi-vendor tourism vehicle-rental marketplace API launching in Andaman & Nicobar Islands.")
                        .contact(new Contact()
                                .name("IslandRide Engineering Team")
                                .email("engineering@islandride.in"))
                        .license(new License()
                                .name("Proprietary Commercial License")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server"),
                        new Server().url("https://api.islandride.in").description("Production Gateway Server")
                ));
    }
}
