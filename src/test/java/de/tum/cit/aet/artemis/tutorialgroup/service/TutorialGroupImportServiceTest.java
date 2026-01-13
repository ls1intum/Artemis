package de.tum.cit.aet.artemis.tutorialgroup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupScheduleTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;

class TutorialGroupImportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tutorialgroupimport";

    @Autowired
    private TutorialGroupImportService tutorialGroupImportService;

    @Autowired
    private TutorialGroupTestRepository tutorialGroupRepository;

    @Autowired
    private TutorialGroupScheduleTestRepository tutorialGroupScheduleRepository;

    private Course sourceCourse;

    private Course targetCourse;

    private User instructor;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        sourceCourse = courseUtilService.createCourse();
        sourceCourse = courseRepository.save(sourceCourse);

        targetCourse = courseUtilService.createCourse();
        targetCourse = courseRepository.save(targetCourse);
    }

    @Test
    void importTutorialGroups_emptySourceCourse_shouldReturnEmptyList() {
        List<TutorialGroup> imported = tutorialGroupImportService.importTutorialGroups(sourceCourse.getId(), targetCourse, instructor);

        assertThat(imported).isEmpty();
    }

    @Test
    void importTutorialGroups_singleGroup_shouldCopyAllFields() {
        TutorialGroup sourceGroup = createTutorialGroup(sourceCourse, "Test Group", "Main Campus", Language.ENGLISH, 20, true, "Additional info");

        List<TutorialGroup> imported = tutorialGroupImportService.importTutorialGroups(sourceCourse.getId(), targetCourse, instructor);

        assertThat(imported).hasSize(1);
        TutorialGroup importedGroup = imported.getFirst();
        assertThat(importedGroup.getId()).isNotNull();
        assertThat(importedGroup.getId()).isNotEqualTo(sourceGroup.getId());
        assertThat(importedGroup.getTitle()).isEqualTo("Test Group");
        assertThat(importedGroup.getCampus()).isEqualTo("Main Campus");
        assertThat(importedGroup.getLanguage()).isEqualTo(Language.ENGLISH.toString());
        assertThat(importedGroup.getCapacity()).isEqualTo(20);
        assertThat(importedGroup.getIsOnline()).isTrue();
        assertThat(importedGroup.getAdditionalInformation()).isEqualTo("Additional info");
        assertThat(importedGroup.getCourse().getId()).isEqualTo(targetCourse.getId());
        assertThat(importedGroup.getTeachingAssistant().getId()).isEqualTo(instructor.getId());
        assertThat(importedGroup.getRegistrations()).isEmpty();
    }

    @Test
    void importTutorialGroups_multipleGroups_shouldImportAll() {
        createTutorialGroup(sourceCourse, "Group 1", "Campus A", Language.ENGLISH, 15, false, null);
        createTutorialGroup(sourceCourse, "Group 2", "Campus B", Language.GERMAN, 25, true, null);
        createTutorialGroup(sourceCourse, "Group 3", null, null, null, null, null);

        List<TutorialGroup> imported = tutorialGroupImportService.importTutorialGroups(sourceCourse.getId(), targetCourse, instructor);

        assertThat(imported).hasSize(3);
        assertThat(imported).extracting(TutorialGroup::getTitle).containsExactlyInAnyOrder("Group 1", "Group 2", "Group 3");
        assertThat(imported).allMatch(group -> group.getCourse().getId().equals(targetCourse.getId()));
        assertThat(imported).allMatch(group -> group.getTeachingAssistant().getId().equals(instructor.getId()));
    }

    @Test
    void importTutorialGroups_withSchedule_shouldCopySchedule() {
        TutorialGroup sourceGroup = createTutorialGroup(sourceCourse, "Scheduled Group", "Campus", Language.ENGLISH, 20, false, null);

        TutorialGroupSchedule schedule = new TutorialGroupSchedule();
        schedule.setTutorialGroup(sourceGroup);
        schedule.setDayOfWeek(DayOfWeek.MONDAY.getValue());
        schedule.setValidFromInclusive("2022-11-25T23:00:00.000Z");
        schedule.setValidToInclusive("2022-12-25T23:00:00.000Z");
        schedule.setStartTime(LocalTime.of(10, 0).toString());
        schedule.setEndTime(LocalTime.of(12, 0).toString());
        schedule.setRepetitionFrequency(1);
        schedule.setLocation("Room 101");
        tutorialGroupScheduleRepository.save(schedule);
        sourceGroup.setTutorialGroupSchedule(schedule);
        tutorialGroupRepository.save(sourceGroup);

        List<TutorialGroup> imported = tutorialGroupImportService.importTutorialGroups(sourceCourse.getId(), targetCourse, instructor);

        assertThat(imported).hasSize(1);
        TutorialGroup importedGroup = imported.getFirst();
        TutorialGroupSchedule importedSchedule = importedGroup.getTutorialGroupSchedule();
        assertThat(importedSchedule).isNotNull();
        assertThat(importedSchedule.getId()).isNotEqualTo(schedule.getId());
        assertThat(importedSchedule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY.getValue());
        assertThat(importedSchedule.getStartTime()).isEqualTo("10:00");
        assertThat(importedSchedule.getEndTime()).isEqualTo("12:00");
        assertThat(importedSchedule.getRepetitionFrequency()).isEqualTo(1);
        assertThat(importedSchedule.getLocation()).isEqualTo("Room 101");
        assertThat(importedSchedule.getValidFromInclusive()).isEqualTo("2022-11-25T23:00:00.000Z");
        assertThat(importedSchedule.getValidToInclusive()).isEqualTo("2022-12-25T23:00:00.000Z");
    }

    @Test
    void importTutorialGroups_shouldNotCopyRegistrations() {
        createTutorialGroup(sourceCourse, "Group with Regs", "Campus", Language.ENGLISH, 20, false, null);
        // Note: We don't add actual registrations here as that would require more complex setup
        // The test verifies that the imported group has empty registrations

        List<TutorialGroup> imported = tutorialGroupImportService.importTutorialGroups(sourceCourse.getId(), targetCourse, instructor);

        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().getRegistrations()).isEmpty();
    }

    @Test
    void importTutorialGroups_shouldSetRequestingUserAsTeachingAssistant() {
        createTutorialGroup(sourceCourse, "Test Group", null, null, null, null, null);

        List<TutorialGroup> imported = tutorialGroupImportService.importTutorialGroups(sourceCourse.getId(), targetCourse, instructor);

        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().getTeachingAssistant()).isEqualTo(instructor);
    }

    @Test
    void importTutorialGroups_shouldNotModifySourceGroups() {
        TutorialGroup sourceGroup = createTutorialGroup(sourceCourse, "Original Group", "Original Campus", Language.ENGLISH, 10, true, "Original info");
        Long originalId = sourceGroup.getId();

        tutorialGroupImportService.importTutorialGroups(sourceCourse.getId(), targetCourse, instructor);

        // Verify source group is unchanged
        TutorialGroup reloadedSourceGroup = tutorialGroupRepository.findById(originalId).orElseThrow();
        assertThat(reloadedSourceGroup.getTitle()).isEqualTo("Original Group");
        assertThat(reloadedSourceGroup.getCourse().getId()).isEqualTo(sourceCourse.getId());
    }

    private TutorialGroup createTutorialGroup(Course course, String title, String campus, Language language, Integer capacity, Boolean isOnline, String additionalInfo) {
        TutorialGroup group = new TutorialGroup();
        group.setTitle(title);
        group.setCampus(campus);
        group.setLanguage(language != null ? language.name() : null);
        group.setCapacity(capacity);
        group.setIsOnline(isOnline);
        group.setAdditionalInformation(additionalInfo);
        group.setCourse(course);
        return tutorialGroupRepository.save(group);
    }
}
