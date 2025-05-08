package de.tum.cit.aet.artemis.programming;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class LocalRepoUrlInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String tempDir = System.getProperty("java.io.tmpdir");
        TestPropertyValues.of("artemis.version-control.url=file:" + tempDir).applyTo(applicationContext);
    }
}
