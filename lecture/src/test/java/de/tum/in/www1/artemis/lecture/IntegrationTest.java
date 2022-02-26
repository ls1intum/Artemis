package de.tum.in.www1.artemis.lecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.in.www1.artemis.lecture.util.JmsTemplateMockConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = LectureApp.class)
@Import(JmsTemplateMockConfiguration.class)
public @interface IntegrationTest {
}
