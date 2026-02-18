package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

class HyperionProgrammingLanguageContextFilterServiceTest {

    private HyperionProgrammingLanguageContextFilterService filterService;

    @BeforeEach
    void setup() {
        filterService = new HyperionProgrammingLanguageContextFilterService();
    }

    // Basic Positive Case
    @Test
    void filter_withJavaLanguage_filtersCorrectly() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/Main.java", "public class Main {}");
        files.put("src/test/java/MainTest.java", "public class MainTest {}");
        files.put("README.md", "# Project README");
        files.put("pom.xml", "<project>...</project>");

        // .gitignore is NOT in SAFE_EXTENSIONS or SAFE_FILENAMES, so it should be excluded
        files.put(".gitignore", "*.class");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).hasSize(4);
        assertThat(result).containsKey("src/main/java/Main.java");
        assertThat(result).containsKey("src/test/java/MainTest.java");
        assertThat(result).containsKey("README.md");
        assertThat(result).containsKey("pom.xml");
        assertThat(result).doesNotContainKey(".gitignore");
    }

    // Global & Language-Specific Exclusions
    @Test
    void filter_withJavaFiles_excludesArtifactsAndSpecifics() {
        Map<String, String> files = new LinkedHashMap<>();
        // Valid (Now included!)
        files.put("src/main/java/Service.java", "public class Service {}");
        files.put("build.gradle", "dependencies {}");

        // Global Exclusions (Must be excluded)
        files.put("target/classes/Service.class", "compiled class");
        files.put(".idea/workspace.xml", "<xml>");
        files.put("node_modules/package/index.js", "module.exports = {}");
        files.put(".git/HEAD", "ref: refs/heads/main");

        // Language-Specific Exclusion (Java strategy excludes gradlew)
        files.put("gradlew", "#!/bin/bash");
        files.put("gradlew.bat", "echo off");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).containsKey("src/main/java/Service.java");
        assertThat(result).containsKey("build.gradle");

        // Assert Global Exclusions
        assertThat(result).doesNotContainKey("target/classes/Service.class");
        assertThat(result).doesNotContainKey(".idea/workspace.xml");
        assertThat(result).doesNotContainKey("node_modules/package/index.js");
        assertThat(result).doesNotContainKey(".git/HEAD");

        // Assert Language-Specific Exclusions
        assertThat(result).doesNotContainKey("gradlew");
        assertThat(result).doesNotContainKey("gradlew.bat");
    }

    // Safety Net & Binaries
    @Test
    void filter_withMixedExtensions_includesAllowedTextOnly() {
        Map<String, String> files = new LinkedHashMap<>();
        // Allowed Text
        files.put("script.js", "console.log('hello');");
        files.put("style.css", "body { color: red; }");
        files.put("data.json", "{ \"key\": \"value\" }");
        files.put("Dockerfile", "FROM java:17");

        // Disallowed / Binary
        files.put("image.png", "binary_data");
        files.put("archive.zip", "binary_data");
        files.put("unknown.bin", "binary_data");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).containsKeys("script.js", "style.css", "data.json", "Dockerfile");
        assertThat(result).doesNotContainKey("image.png");
        assertThat(result).doesNotContainKey("archive.zip");
        assertThat(result).doesNotContainKey("unknown.bin");
    }

    // Size Guard
    @Test
    void filter_withLargeFile_excludesContent() {
        Map<String, String> files = new LinkedHashMap<>();

        // Create a string larger than 100KB (100 * 1024 + 1 bytes)
        String largeContent = IntStream.range(0, 102401).mapToObj(i -> "a").collect(Collectors.joining(""));

        files.put("src/Large.java", largeContent);
        files.put("src/Small.java", "small content");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).containsKey("src/Small.java");
        assertThat(result).doesNotContainKey("src/Large.java");
    }

    // Unregistered Language
    @Test
    void filter_withUnsupportedLanguage_fallsBackToGlobalStrategy() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("main.dart", "void main() {}"); // Safe extension
        files.put("node_modules/pkg.json", "{}"); // Global exclusion

        // Passing null simulates an unregistered or unknown language, falling back to default
        Map<String, String> result = filterService.filter(files, null);

        assertThat(result).containsKey("main.dart");
        assertThat(result).doesNotContainKey("node_modules/pkg.json");
    }

    // Null/Empty Handling
    @Test
    void filter_withEmptyOrNull_returnsEmpty() {
        assertThat(filterService.filter(new LinkedHashMap<>(), ProgrammingLanguage.JAVA)).isEmpty();
        assertThat(filterService.filter(null, ProgrammingLanguage.JAVA)).isEmpty();
    }
}
