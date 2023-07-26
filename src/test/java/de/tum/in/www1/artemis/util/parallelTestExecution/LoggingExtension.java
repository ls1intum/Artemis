package de.tum.in.www1.artemis.util.parallelTestExecution;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.MDC;

public class LoggingExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        MDC.put("logFileName", context.getTestClass().orElseThrow().getSimpleName() + "/" + context.getTestMethod().orElseThrow().getName());
    }
}
