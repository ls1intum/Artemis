package de.tum.in.www1.artemis.lecture.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebConfigurerTestController {

    /**
     * Test endpoint
     */
    @GetMapping("/api/test-cors")
    public void testCorsOnApiPath() {
    }

    /**
     * Test endpoint
     */
    @GetMapping("/test/test-cors")
    public void testCorsOnOtherPath() {
    }
}
