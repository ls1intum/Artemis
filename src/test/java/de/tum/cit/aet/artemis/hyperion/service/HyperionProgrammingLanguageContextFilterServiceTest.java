package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

class HyperionProgrammingLanguageContextFilterServiceTest {

    private HyperionProgrammingLanguageContextFilterService filterService;

    @BeforeEach
    void setup() {
        filterService = new HyperionProgrammingLanguageContextFilterService();
    }

    @Test
    void filter_withJavaLanguage_filtersCorrectly() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/Main.java", "public class Main {}");
        files.put("src/test/java/MainTest.java", "public class MainTest {}");
        files.put("README.md", "# Project README");
        files.put("pom.xml", "<project>...</project>");
        files.put(".gitignore", "*.class");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).hasSize(2);
        assertThat(result).containsKey("src/main/java/Main.java");
        assertThat(result).containsKey("src/test/java/MainTest.java");
        assertThat(result).doesNotContainKey("README.md");
        assertThat(result).doesNotContainKey("pom.xml");
        assertThat(result).doesNotContainKey(".gitignore");
    }

    @Test
    void filter_withUnsupportedLanguage_returnsAllFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("main.py", "print('hello')");
        files.put("test.py", "assert True");
        files.put("README.md", "# Python Project");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.PYTHON);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrderEntriesOf(files);
    }

    @Test
    void filter_withEmptyFiles_returnsEmpty() {
        Map<String, String> files = new LinkedHashMap<>();

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).isEmpty();
    }

    @Test
    void filter_withNullFiles_returnsEmpty() {
        Map<String, String> result = filterService.filter(null, ProgrammingLanguage.JAVA);

        assertThat(result).isEmpty();
    }

    @Test
    void filter_withJavaFiles_excludesNonSourceFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/com/example/Service.java", "public class Service {}");
        files.put("src/main/java/com/example/Controller.java", "public class Controller {}");
        files.put("target/classes/Service.class", "compiled class");
        files.put("build.gradle", "dependencies {}");
        files.put("settings.gradle", "rootProject.name = 'test'");
        files.put(".idea/workspace.xml", "<xml>");
        files.put("node_modules/package/index.js", "module.exports = {}");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).hasSize(2);
        assertThat(result).containsKey("src/main/java/com/example/Service.java");
        assertThat(result).containsKey("src/main/java/com/example/Controller.java");
    }

    @Test
    void filter_withMixedExtensions_filtersOnlyJavaFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/Main.java", "public class Main {}");
        files.put("src/test/java/Test.java", "public class Test {}");
        files.put("style.css", "body { color: red; }");
        files.put("script.js", "console.log('hello');");
        files.put("data.json", "{ \"key\": \"value\" }");
        files.put("config.xml", "<config/>");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).hasSize(2);
        assertThat(result).containsKey("src/main/java/Main.java");
        assertThat(result).containsKey("src/test/java/Test.java");
    }
}
