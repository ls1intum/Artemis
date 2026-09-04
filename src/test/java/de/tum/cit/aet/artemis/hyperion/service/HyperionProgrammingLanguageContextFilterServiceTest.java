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

    @Test
    void filter_withJavaLanguage_filtersCorrectly() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/Main.java", "public class Main {}");
        files.put("src/test/java/MainTest.java", "public class MainTest {}");
        files.put("README.md", "# Project README");
        files.put("pom.xml", "<project>...</project>");

        // ".gitignore" is neither a safe extension nor a safe filename.
        files.put(".gitignore", "*.class");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).hasSize(4);
        assertThat(result).containsKey("src/main/java/Main.java");
        assertThat(result).containsKey("src/test/java/MainTest.java");
        assertThat(result).containsKey("README.md");
        assertThat(result).containsKey("pom.xml");
        assertThat(result).doesNotContainKey(".gitignore");
    }

    @Test
    void filter_withJavaFiles_excludesArtifactsAndSpecifics() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/Service.java", "public class Service {}");
        files.put("build.gradle", "dependencies {}");

        files.put("target/classes/Service.class", "compiled class");
        files.put(".idea/workspace.xml", "<xml>");
        files.put("node_modules/package/index.js", "module.exports = {}");
        files.put(".git/HEAD", "ref: refs/heads/main");

        // The Java strategy excludes the Gradle wrapper scripts.
        files.put("gradlew", "#!/bin/bash");
        files.put("gradlew.bat", "echo off");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).containsKey("src/main/java/Service.java");
        assertThat(result).containsKey("build.gradle");

        assertThat(result).doesNotContainKey("target/classes/Service.class");
        assertThat(result).doesNotContainKey(".idea/workspace.xml");
        assertThat(result).doesNotContainKey("node_modules/package/index.js");
        assertThat(result).doesNotContainKey(".git/HEAD");

        assertThat(result).doesNotContainKey("gradlew");
        assertThat(result).doesNotContainKey("gradlew.bat");
    }

    @Test
    void filter_withMixedExtensions_includesAllowedTextOnly() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("script.js", "console.log('hello');");
        files.put("style.css", "body { color: red; }");
        files.put("data.json", "{ \"key\": \"value\" }");
        files.put("Dockerfile", "FROM java:17");

        files.put("image.png", "binary_data");
        files.put("archive.zip", "binary_data");
        files.put("unknown.bin", "binary_data");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).containsKeys("script.js", "style.css", "data.json", "Dockerfile");
        assertThat(result).doesNotContainKey("image.png");
        assertThat(result).doesNotContainKey("archive.zip");
        assertThat(result).doesNotContainKey("unknown.bin");
    }

    @Test
    void filter_withLargeFile_excludesContent() {
        Map<String, String> files = new LinkedHashMap<>();

        // One byte past the 100 KB per-file limit.
        String largeContent = IntStream.range(0, 102401).mapToObj(i -> "a").collect(Collectors.joining(""));

        files.put("src/Large.java", largeContent);
        files.put("src/Small.java", "small content");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).containsKey("src/Small.java");
        assertThat(result).doesNotContainKey("src/Large.java");
    }

    @Test
    void filter_withUnsupportedLanguage_fallsBackToGlobalStrategy() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("main.dart", "void main() {}");
        files.put("node_modules/pkg.json", "{}");

        // A null language stands in for an unregistered one and falls back to the global strategy.
        Map<String, String> result = filterService.filter(files, null);

        assertThat(result).containsKey("main.dart");
        assertThat(result).doesNotContainKey("node_modules/pkg.json");
    }

    @Test
    void filter_withEmptyOrNull_returnsEmpty() {
        assertThat(filterService.filter(new LinkedHashMap<>(), ProgrammingLanguage.JAVA)).isEmpty();
        assertThat(filterService.filter(null, ProgrammingLanguage.JAVA)).isEmpty();
    }

    @Test
    void filter_excludesCredentialPathsAndSupportedMaterialWithoutBlockingOrdinarySource() {
        String githubSentinel = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";
        Map<String, String> files = new LinkedHashMap<>();
        // ".json" is a safe extension, so this file can only be rejected by the credential-path gate, not by the extension net.
        files.put("service-account.json", "{\"private_key\": \"-----BEGIN PRIVATE KEY-----\\nfixture-key-material\\n-----END PRIVATE KEY-----\"}");
        files.put("src/sentinel.txt", githubSentinel);
        files.put("src/Example.java", "String token = \"token\"; String password = \"change-me\"; String apiKey = \"example\";");

        Map<String, String> result = filterService.filter(files, ProgrammingLanguage.JAVA);

        assertThat(result).containsOnlyKeys("src/Example.java");
    }
}
