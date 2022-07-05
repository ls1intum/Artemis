package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.service.util.Tuple;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TitleCacheEvictionServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApollonDiagramRepository apollonDiagramRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void testEvictsTitleOnUpdateTitleOrDeleteCourse() {
        var course = database.addEmptyCourse();
        testCacheEvicted("courseTitle", () -> new Tuple<>(course.getId(), course.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    course.setTitle(UUID.randomUUID().toString());
                    courseRepository.save(course);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    course.setDescription(UUID.randomUUID().toString()); // Change some other values
                    courseRepository.save(course);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    courseRepository.delete(course);
                    return true;
                }));
    }

    @Test
    public void testEvictsTitleOnUpdateTitleOrDeleteExercise() {
        var course = database.addCourseWithOneReleasedTextExercise();
        var exercise = course.getExercises().stream().findAny().orElseThrow();
        testCacheEvicted("exerciseTitle", () -> new Tuple<>(exercise.getId(), exercise.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    exercise.setTitle(UUID.randomUUID().toString());
                    exerciseRepository.save(exercise);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    exercise.setProblemStatement(UUID.randomUUID().toString()); // Change some other values
                    exerciseRepository.save(exercise);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    exerciseRepository.delete(exercise);
                    return true;
                }));
    }

    @Test
    public void testEvictsTitleOnUpdateTitleOrDeleteLecture() {
        var lecture = database.createCourseWithLecture(true);
        testCacheEvicted("lectureTitle", () -> new Tuple<>(lecture.getId(), lecture.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    lecture.setTitle(UUID.randomUUID().toString());
                    lectureRepository.save(lecture);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    lecture.setDescription(UUID.randomUUID().toString()); // Change some other values
                    lectureRepository.save(lecture);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    lectureRepository.delete(lecture);
                    return true;
                }));
    }

    @Test
    public void testEvictsTitleOnUpdateNameOrDeleteOrganization() {
        var org = database.createOrganization();
        testCacheEvicted("organizationTitle", () -> new Tuple<>(org.getId(), org.getName()), List.of(
                // Should evict as we change the name
                () -> {
                    org.setName(UUID.randomUUID().toString());
                    organizationRepository.save(org);
                    return true;
                },
                // Should not evict as name remains the same
                () -> {
                    org.setDescription(UUID.randomUUID().toString()); // Change some other values
                    organizationRepository.save(org);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    organizationRepository.delete(org);
                    return true;
                }));
    }

    @Test
    public void testEvictsTitleOnUpdateTitleOrDeleteApollonDiagram() {
        var apollonDiagram = ModelFactory.generateApollonDiagram(DiagramType.ActivityDiagram, "activityDiagram1");
        var fApollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        testCacheEvicted("diagramTitle", () -> new Tuple<>(fApollonDiagram.getId(), fApollonDiagram.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    fApollonDiagram.setTitle(UUID.randomUUID().toString());
                    apollonDiagramRepository.save(fApollonDiagram);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    fApollonDiagram.setDiagramType(DiagramType.ClassDiagram); // Change some other values
                    apollonDiagramRepository.save(fApollonDiagram);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    apollonDiagramRepository.delete(apollonDiagram);
                    return true;
                }));
    }

    @Test
    public void testEvictsTitleOnUpdateTitleOrDeleteExam() {
        var course = database.createCourse();
        var exam = database.addExam(course);
        testCacheEvicted("examTitle", () -> new Tuple<>(exam.getId(), exam.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    exam.setTitle(UUID.randomUUID().toString());
                    examRepository.save(exam);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    exam.setConfirmationEndText(UUID.randomUUID().toString()); // Change some other values
                    examRepository.save(exam);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    examRepository.delete(exam);
                    return true;
                }));
    }

    @Test
    public void testEvictsTitleOnUpdateTitleOrDeleteExerciseHint() {
        var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        database.addHintsToExercise(exercise);
        var hint = exercise.getExerciseHints().stream().findFirst().orElseThrow();
        testCacheEvicted("exerciseHintTitle", () -> new Tuple<>(exercise.getId() + "-" + hint.getId(), hint.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    hint.setTitle(UUID.randomUUID().toString());
                    exerciseHintRepository.save(hint);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    hint.setDescription(UUID.randomUUID().toString()); // Change some other values
                    exerciseHintRepository.save(hint);
                    return false;
                },
                // Should not do something if the exercise is missing
                () -> {
                    hint.setExercise(null);
                    hint.setTitle(UUID.randomUUID().toString());
                    exerciseHintRepository.save(hint);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    hint.setExercise(exercise);
                    exerciseHintRepository.delete(hint);
                    return true;
                }));
    }

    private void testCacheEvicted(String cacheName, Supplier<Tuple<Object, String>> idTitleSupplier, List<Supplier<Boolean>> entityModifiers) {
        var cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();

        for (var modifier : entityModifiers) {
            var objInCache = idTitleSupplier.get();
            cache.put(objInCache.x(), objInCache.y());
            var cacheValueWrapper = cache.get(objInCache.x());
            assertThat(cacheValueWrapper).isNotNull();
            assertThat(cacheValueWrapper.get()).isEqualTo(objInCache.y());

            boolean shouldEvict = modifier.get();
            cacheValueWrapper = cache.get(objInCache.x());
            if (shouldEvict) {
                assertThat(cacheValueWrapper).isNull();
            }
            else {
                assertThat(cacheValueWrapper).isNotNull();
                assertThat(cacheValueWrapper.get()).isEqualTo(objInCache.y());
            }
        }
    }
}
