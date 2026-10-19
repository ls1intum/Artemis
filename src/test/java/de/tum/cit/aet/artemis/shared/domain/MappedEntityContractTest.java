package de.tum.cit.aet.artemis.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseAssociation_;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseTimeline;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.CourseNotificationParameter;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.repository.CourseNotificationRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingSpecificationRepository;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationParameterTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/** Contracts for mapped state shared by entities with independent tables and composite keys. */
class MappedEntityContractTest extends AbstractSpringIntegrationIndependentTest {

    private static final Map<String, String> TIMELINE_COLUMNS = Map.of("releaseDate", "release_date", "startDate", "start_date", "dueDate", "due_date", "assessmentDueDate",
            "assessment_due_date", "exampleSolutionPublicationDate", "example_solution_publication_date");

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private ExerciseVariantGroupRepository groups;

    @Autowired
    private ExerciseConfigurationService configurations;

    @Autowired
    private CourseNotificationRepository notifications;

    @Autowired
    private CourseNotificationParameterTestRepository parameters;

    @Autowired
    private UserCourseNotificationStatusTestRepository statuses;

    @Autowired
    private UserCourseRoleTestRepository roles;

    @Autowired
    private UserCourseNotificationSettingPresetRepository presets;

    @Autowired
    private UserCourseNotificationSettingSpecificationRepository specifications;

    @Test
    void timelineKeepsTablesColumnsDiscriminatorsAndNullableDates() {
        Map<Class<?>, String> discriminators = Map.of(TextExercise.class, "T", FileUploadExercise.class, "F", ModelingExercise.class, "M", ProgrammingExercise.class, "P",
                QuizExercise.class, "Q");
        for (Class<?> type : List.of(TextExercise.class, FileUploadExercise.class, ModelingExercise.class, ProgrammingExercise.class, QuizExercise.class,
                ExerciseVariantGroup.class)) {
            var mapping = mapping(type);
            assertThat(mapping.getTableName()).isEqualTo(type == ExerciseVariantGroup.class ? "exercise_variant_group" : "exercise");
            assertThat(mapping.getIdentifierColumnNames()).containsExactly("id");
            for (var column : TIMELINE_COLUMNS.entrySet()) {
                assertThat(mapping.getPropertyColumnNames(column.getKey())).containsExactly(column.getValue());
                int index = Arrays.asList(mapping.getPropertyNames()).indexOf(column.getKey());
                assertThat(mapping.getPropertyNullability()[index]).isTrue();
                assertThat(mapping.getPropertyType(column.getKey()).getReturnedClass()).isEqualTo(ZonedDateTime.class);
            }
            if (type != ExerciseVariantGroup.class) {
                assertThat(mapping.getDiscriminatorColumnName()).isEqualTo("discriminator");
                assertThat(String.valueOf(mapping.getDiscriminatorValue())).isEqualTo(discriminators.get(type));
            }
        }
    }

    @Test
    void everyTimelinePersistsAndRetainsTopLevelJsonDates() throws Exception {
        var course = courseUtilService.createCourse();
        List<ExerciseTimeline> timelines = List.of(new TextExercise(), new FileUploadExercise(), new ModelingExercise(), new ProgrammingExercise(), new QuizExercise(),
                new ExerciseVariantGroup());
        for (var timeline : timelines) {
            if (timeline instanceof Exercise exercise) {
                exercise.setTitle("Mapped timeline");
                exercise.setCourse(course);
            }
            else if (timeline instanceof ExerciseVariantGroup group) {
                group.setTitle("Mapped timeline");
                group.setCourse(course);
            }
            var emptyJson = mapper.valueToTree(timeline);
            for (String property : TIMELINE_COLUMNS.keySet()) {
                assertThat(emptyJson.has(property)).as(property).isFalse();
            }
            var start = ZonedDateTime.parse("2026-01-01T10:00:00Z");
            timeline.setReleaseDate(start);
            timeline.setStartDate(start.plusHours(1));
            timeline.setDueDate(start.plusHours(2));
            timeline.setAssessmentDueDate(start.plusHours(3));
            timeline.setExampleSolutionPublicationDate(start.plusHours(4));
            // Serialize the transient entity, without unrelated lazy associations from a persistence context.
            var json = mapper.valueToTree(timeline);
            assertThat(json.has("timeline")).isFalse();
            for (String property : TIMELINE_COLUMNS.keySet()) {
                assertThat(json.has(property)).as(property).isTrue();
            }
            ExerciseTimeline decoded = timeline instanceof Exercise ? mapper.treeToValue(json, Exercise.class) : mapper.treeToValue(json, ExerciseVariantGroup.class);
            assertDates(decoded, start);
            if (timeline instanceof Exercise exercise) {
                exerciseRepository.saveAndFlush(exercise);
                configurations.initialize(exercise);
            }
            else {
                groups.saveAndFlush((ExerciseVariantGroup) timeline);
            }
            assertDates(load(timeline.getClass(), timeline.getId()), start);
            timeline.setReleaseDate(null);
            timeline.setStartDate(null);
            timeline.setDueDate(null);
            timeline.setAssessmentDueDate(null);
            timeline.setExampleSolutionPublicationDate(null);
            if (timeline instanceof Exercise exercise) {
                exerciseRepository.saveAndFlush(exercise);
            }
            else {
                groups.saveAndFlush((ExerciseVariantGroup) timeline);
            }
            var empty = load(timeline.getClass(), timeline.getId());
            assertThat(empty.getReleaseDate()).isNull();
            assertThat(empty.getStartDate()).isNull();
            assertThat(empty.getDueDate()).isNull();
            assertThat(empty.getAssessmentDueDate()).isNull();
            assertThat(empty.getExampleSolutionPublicationDate()).isNull();
        }
    }

    @Test
    void assignedKeysKeepNewnessCallbacksAndUpdateRatherThanInsertLoadedRows() {
        userUtilService.addUsers("mappednotifications", 1, 0, 0, 0);
        var user = userUtilService.getUserByLogin("mappednotificationsstudent1");
        var course = courseUtilService.createCourse();
        var notification = notifications.saveAndFlush(new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now().plusDays(7)));
        var parameter = new CourseNotificationParameter(notification, "key", "before");
        var status = new UserCourseNotificationStatus(notification, user, UserCourseNotificationStatusType.UNSEEN);
        assertThat(parameter.isNew()).isTrue();
        assertThat(status.isNew()).isTrue();
        parameters.saveAndFlush(parameter);
        statuses.saveAndFlush(status);
        assertThat(parameter.isNew()).isFalse();
        assertThat(status.isNew()).isFalse();
        var loadedParameter = load(CourseNotificationParameter.class, parameter.getId());
        var loadedStatus = load(UserCourseNotificationStatus.class, status.getId());
        assertThat(loadedParameter.isNew()).isFalse();
        assertThat(loadedStatus.isNew()).isFalse();
        loadedParameter.setValue("after");
        loadedStatus.setStatus(UserCourseNotificationStatusType.SEEN);
        parameters.saveAndFlush(loadedParameter);
        statuses.saveAndFlush(loadedStatus);
        assertThat(load(CourseNotificationParameter.class, parameter.getId()).getValue()).isEqualTo("after");
        assertThat(load(UserCourseNotificationStatus.class, status.getId()).getStatus()).isEqualTo(UserCourseNotificationStatusType.SEEN);
        // Serialize fresh, initialized instances so association loading is not part of this JSON contract.
        notification = new CourseNotification();
        notification.setId(1L);
        user = new de.tum.cit.aet.artemis.account.domain.User();
        user.setId(1L);
        parameter = new CourseNotificationParameter(notification, "key", "value");
        status = new UserCourseNotificationStatus(notification, user, UserCourseNotificationStatusType.UNSEEN);
        assertThat(mapper.valueToTree(parameter).has("new")).isFalse();
        assertThat(mapper.valueToTree(status).has("new")).isFalse();
        assertThat(mapper.valueToTree(parameter).has("id")).isFalse();
        assertThat(mapper.valueToTree(status).has("id")).isFalse();
        assertThat(Arrays.asList(mapping(CourseNotificationParameter.class).getPropertyNames())).doesNotContain("isNew");
        assertThat(Arrays.asList(mapping(UserCourseNotificationStatus.class).getPropertyNames())).doesNotContain("isNew");
    }

    @Test
    void userCourseKeysRetainExtraComponentsLazyReferencesAndPublicSetterBoundaries() throws Exception {
        userUtilService.addUsers("mappedassociation", 1, 0, 0, 0);
        var user = userUtilService.getUserByLogin("mappedassociationstudent1");
        var course = courseUtilService.createCourse();
        roles.saveAndFlush(new UserCourseRole(user, course, CourseRole.STUDENT));
        roles.saveAndFlush(new UserCourseRole(user, course, CourseRole.EDITOR));
        assertThat(load(UserCourseRole.class, new UserCourseRole.UserCourseRoleId(user.getId(), course.getId(), CourseRole.STUDENT)).getRole()).isEqualTo(CourseRole.STUDENT);
        assertThat(load(UserCourseRole.class, new UserCourseRole.UserCourseRoleId(user.getId(), course.getId(), CourseRole.EDITOR)).getRole()).isEqualTo(CourseRole.EDITOR);
        var preset = new UserCourseNotificationSettingPreset();
        preset.setUser(user);
        preset.setCourse(course);
        preset.setSettingPreset((short) 2);
        presets.saveAndFlush(preset);
        assertThat(load(UserCourseNotificationSettingPreset.class, new UserCourseNotificationSettingPreset.UserCourseNotificationSettingPresetId(user.getId(), course.getId()))
                .getSettingPreset()).isEqualTo((short) 2);
        for (short type : new short[] { 1, 2 }) {
            var specification = new UserCourseNotificationSettingSpecification(user, course, type, true, false, true, false);
            specification.setUser(user);
            specification.setCourse(course);
            specifications.saveAndFlush(specification);
            assertThat(load(UserCourseNotificationSettingSpecification.class,
                    new UserCourseNotificationSettingSpecification.UserCourseNotificationSettingSpecificationId(user.getId(), course.getId(), type)).getCourseNotificationType())
                    .isEqualTo(type);
        }
        assertThat(presets.findSettingPresetByUserIdAndCourseId(user.getId(), course.getId())).isEqualTo((short) 2);
        assertThat(specifications.findAllByUserIdAndCourseIdAndCourseNotificationTypeIn(user.getId(), course.getId(), List.of((short) 1, (short) 2))).hasSize(2);
        assertThatThrownBy(() -> UserCourseRole.class.getMethod("setUser", de.tum.cit.aet.artemis.account.domain.User.class)).isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> UserCourseRole.class.getMethod("setCourse", de.tum.cit.aet.artemis.course.domain.Course.class)).isInstanceOf(NoSuchMethodException.class);
        assertThat(preset.getClass().getMethod("setUser", de.tum.cit.aet.artemis.account.domain.User.class)).isNotNull();
        assertThat(preset.getClass().getMethod("setCourse", de.tum.cit.aet.artemis.course.domain.Course.class)).isNotNull();
        for (Class<?> type : List.of(UserCourseRole.class, UserCourseNotificationSettingPreset.class, UserCourseNotificationSettingSpecification.class)) {
            var mapping = mapping(type);
            assertThat(mapping.getIdentifierColumnNames()).contains("user_id", "course_id");
            try (var entityManager = entityManagerFactory.createEntityManager()) {
                Object key = type == UserCourseRole.class ? new UserCourseRole.UserCourseRoleId(user.getId(), course.getId(), CourseRole.STUDENT)
                        : type == UserCourseNotificationSettingPreset.class
                                ? new UserCourseNotificationSettingPreset.UserCourseNotificationSettingPresetId(user.getId(), course.getId())
                                : new UserCourseNotificationSettingSpecification.UserCourseNotificationSettingSpecificationId(user.getId(), course.getId(), (short) 1);
                Object entity = entityManager.find(type, key);
                assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(entity, UserCourseAssociation_.USER)).isFalse();
                assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(entity, UserCourseAssociation_.COURSE)).isFalse();
            }
        }
    }

    private AbstractEntityPersister mapping(Class<?> type) {
        return (AbstractEntityPersister) entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel().getEntityDescriptor(type);
    }

    private <T> T load(Class<T> type, Object id) {
        try (var entityManager = entityManagerFactory.createEntityManager()) {
            return entityManager.find(type, id);
        }
    }

    private static void assertDates(ExerciseTimeline timeline, ZonedDateTime start) {
        assertThat(timeline.getReleaseDate()).isEqualTo(start);
        assertThat(timeline.getStartDate()).isEqualTo(start.plusHours(1));
        assertThat(timeline.getDueDate()).isEqualTo(start.plusHours(2));
        assertThat(timeline.getAssessmentDueDate()).isEqualTo(start.plusHours(3));
        assertThat(timeline.getExampleSolutionPublicationDate()).isEqualTo(start.plusHours(4));
    }
}
