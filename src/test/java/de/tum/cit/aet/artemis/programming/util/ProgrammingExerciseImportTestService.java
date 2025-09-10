package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

/**
 * Test service for handling programming exercise imports
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ProgrammingExerciseImportTestService {

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Functional interface to modify the exercise before import
     */
    public interface ExerciseModifier<T> {

        T modify(ProgrammingExercise exercise);
    }

    /**
     * Result record holding data related to a programming exercise import
     */
    public record ImportFileResult(ClassPathResource resource, ProgrammingExercise parsedExercise, ProgrammingExercise importedExercise, Object additionalData) {
    }

    /**
     * Prepares and imports a programming exercise from a zip file
     *
     * @param resourcePath Path to the resource zip file
     * @param modifier     Function to modify the exercise before import
     * @param course       Course to import the exercise into
     * @return ImportFileResult containing the resource, parsed exercise, imported exercise and any additional data
     * @throws Exception if the import fails
     */
    public ImportFileResult prepareExerciseImport(String resourcePath, ExerciseModifier<?> modifier, Course course) throws Exception {
        var resource = new ClassPathResource(resourcePath);
        ZipInputStream zipInputStream = new ZipInputStream(resource.getInputStream());
        String detailsJsonString = null;
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.getName().endsWith(".json")) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = zipInputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                detailsJsonString = outputStream.toString(StandardCharsets.UTF_8);
                break;
            }
        }
        zipInputStream.close();
        assertThat(detailsJsonString).isNotNull();

        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.findAndRegisterModules();
        ProgrammingExercise parsedExercise = objectMapper.readValue(detailsJsonString, ProgrammingExercise.class);

        if (parsedExercise.getBuildConfig() == null) {
            parsedExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        }

        Object additionalData = modifier.modify(parsedExercise);

        parsedExercise.setCourse(course);
        parsedExercise.setId(null);
        parsedExercise.setChannelName("testchannel-pe-imported");
        parsedExercise.forceNewProjectKey();

        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());

        ProgrammingExercise importedExercise = request.postWithMultipartFile("/api/programming/courses/" + course.getId() + "/programming-exercises/import-from-file",
                parsedExercise, "programmingExercise", file, ProgrammingExercise.class, HttpStatus.OK);

        return new ImportFileResult(resource, parsedExercise, importedExercise, additionalData);
    }

    /**
     * Counts occurrences of a string in a zip file
     *
     * @param resource     Resource containing the zip file
     * @param searchString String to search for
     * @return Count of occurrences
     * @throws Exception if the zip file cannot be read
     */
    public int countOccurrencesInZip(ClassPathResource resource, String searchString) throws Exception {
        int occurrenceCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(resource.getFile().toPath()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".zip")) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = zipInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    String content = outputStream.toString(StandardCharsets.UTF_8);
                    int currentPosition = 0;
                    while ((currentPosition = content.indexOf(searchString, currentPosition)) != -1) {
                        occurrenceCount++;
                        currentPosition += searchString.length();
                    }
                }
            }
        }
        return occurrenceCount;
    }

    /**
     * Counts occurrences of a string in files within a directory
     *
     * @param path         Directory path to search in
     * @param searchString String to search for
     * @return Count of occurrences
     * @throws IOException if the directory cannot be read
     */
    public int countOccurrencesInDirectory(Path path, String searchString) throws IOException {
        int occurrenceCount = 0;
        if (!Files.exists(path)) {
            throw new IOException("Directory does not exist");
        }
        List<Path> files = new ArrayList<>();
        Files.walk(path).filter(Files::isRegularFile).forEach(files::add);
        for (Path filePath : files) {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            int currentPosition = 0;
            while ((currentPosition = content.indexOf(searchString, currentPosition)) != -1) {
                occurrenceCount++;
                currentPosition += searchString.length();
            }
        }
        return occurrenceCount;
    }
}
