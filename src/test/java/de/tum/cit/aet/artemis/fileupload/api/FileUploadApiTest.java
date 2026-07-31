package de.tum.cit.aet.artemis.fileupload.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ImportedExerciseAssertions;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FileUploadApiTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "fileuploadapitest";

    @Autowired
    private FileUploadImportApi fileUploadImportApi;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportFileUploadExerciseViaApi() {
        Course sourceCourse = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        long sourceExerciseId = sourceCourse.getExercises().stream().findFirst().orElseThrow().getId();
        // Re-read the source so its grading criteria are initialized for the content assertions.
        FileUploadExercise sourceExercise = fileUploadImportApi.findWithGradingCriteriaByIdElseThrow(sourceExerciseId);

        Course targetCourse = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(targetCourse);
        Competency targetCompetency = competencyUtilService.createCompetency(targetCourse);

        // Mirror a real caller (CourseMaterialImportService): a fresh skeleton carrying the destination, a competency link
        // of the target course, and the fields whose non-null defaults the import service cannot tell apart from an
        // intentional value. The skeleton and the source must be distinct objects, otherwise the assertions below would
        // compare the imported exercise with itself.
        FileUploadExercise newExercise = new FileUploadExercise();
        newExercise.setCourse(targetCourse);
        newExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        newExercise.setTitle(sourceExercise.getTitle());
        newExercise.setMaxPoints(sourceExercise.getMaxPoints());
        newExercise.setBonusPoints(sourceExercise.getBonusPoints());
        newExercise.setIncludedInOverallScore(sourceExercise.getIncludedInOverallScore());
        newExercise.setGradingCriteria(null);
        newExercise.setCompetencyLinks(new HashSet<>(Set.of(new CompetencyExerciseLink(targetCompetency, newExercise, 1))));

        Optional<FileUploadExercise> optionalImportedFileUploadExercise = fileUploadImportApi.importFileUploadExercise(sourceExerciseId, newExercise);
        assertThat(optionalImportedFileUploadExercise).isPresent();
        FileUploadExercise importedFileUploadExercise = optionalImportedFileUploadExercise.get();

        assertThat(importedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(targetCourse.getId());
        ImportedExerciseAssertions.assertContentPreserved(sourceExercise, importedFileUploadExercise);
        // The competency link is created for the persisted exercise, so the returned exercise must carry it with an id.
        assertThat(importedFileUploadExercise.getCompetencyLinks()).hasSize(1);
        CompetencyExerciseLink importedLink = importedFileUploadExercise.getCompetencyLinks().iterator().next();
        assertThat(importedLink.getCompetency().getId()).isEqualTo(targetCompetency.getId());
        assertThat(importedLink.getExercise().getId()).isEqualTo(importedFileUploadExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindFileUploadExerciseWithCompetencyViaApi() throws NoUniqueQueryException {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        FileUploadExercise expectedFileUploadExercise = (FileUploadExercise) course.getExercises().stream().findFirst().orElseThrow();

        Optional<FileUploadExercise> optionalExercise = fileUploadImportApi.findUniqueWithCompetenciesByTitleAndCourseId(expectedFileUploadExercise.getTitle(), course.getId());
        assertThat(optionalExercise).isPresent();
        assertThat(optionalExercise.get().getId()).isEqualTo(expectedFileUploadExercise.getId());
    }
}
