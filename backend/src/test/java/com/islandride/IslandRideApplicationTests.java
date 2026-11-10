package com.islandride;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class IslandRideApplicationTests {

    @Test
    @DisplayName("Verify Spring Boot Application Context Loads Successfully")
    void contextLoads() {
        assertTrue(true, "Application context initialized without failure");
    }
}
