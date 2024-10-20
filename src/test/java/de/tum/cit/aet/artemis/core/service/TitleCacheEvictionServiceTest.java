package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import de.tum.cit.aet.artemis.core.organization.util.OrganizationUtilService;
import de.tum.cit.aet.artemis.core.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.Tuple;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.repository.ApollonDiagramRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.hestia.ExerciseHintRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Test for {@link TitleCacheEvictionService} that should evict entity titles from the title caches if the titles are
 * updated, but leave them untouched if the titles don't change.
 *
 * The service is not directly injected / used here as it listens to Hibernate events, so we just apply
 * CRUD operations on the entities it supports.
 */
class TitleCacheEvictionServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private CacheManager cacheManager;

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

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private OrganizationUtilService organizationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Test
    void testEvictsTitleOnUpdateTitleOrDeleteCourse() {
        var course = courseUtilService.addEmptyCourse();
        testCacheEvicted("courseTitle", () -> new Tuple<>(course.getId(), course.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    course.setTitle("testEvictsTitleOnUpdateTitleOrDeleteCourse");
                    courseRepository.save(course);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    course.setDescription("testEvictsTitleOnUpdateTitleOrDeleteCourse"); // Change some other values
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
    void testEvictsTitleOnUpdateTitleOrDeleteExercise() {
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        var exercise = course.getExercises().stream().findAny().orElseThrow();
        testCacheEvicted("exerciseTitle", () -> new Tuple<>(exercise.getId(), exercise.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    exercise.setTitle("testEvictsTitleOnUpdateTitleOrDeleteExercise");
                    exerciseRepository.save(exercise);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    exercise.setProblemStatement("testEvictsTitleOnUpdateTitleOrDeleteExercise"); // Change some other values
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
    void testEvictsTitleOnUpdateTitleOrDeleteLecture() {
        var lecture = lectureUtilService.createCourseWithLecture(true);
        testCacheEvicted("lectureTitle", () -> new Tuple<>(lecture.getId(), lecture.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    lecture.setTitle("testEvictsTitleOnUpdateTitleOrDeleteLecture");
                    lectureRepository.save(lecture);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    lecture.setDescription("testEvictsTitleOnUpdateTitleOrDeleteLecture"); // Change some other values
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
    void testEvictsTitleOnUpdateNameOrDeleteOrganization() {
        var org = organizationUtilService.createOrganization();
        testCacheEvicted("organizationTitle", () -> new Tuple<>(org.getId(), org.getName()), List.of(
                // Should evict as we change the name
                () -> {
                    org.setName("testEvictsTitleOnUpdateNameOrDeleteOrganization");
                    organizationRepository.save(org);
                    return true;
                },
                // Should not evict as name remains the same
                () -> {
                    org.setDescription("testEvictsTitleOnUpdateNameOrDeleteOrganization"); // Change some other values
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
    void testEvictsTitleOnUpdateTitleOrDeleteApollonDiagram() {
        var apollonDiagram = apollonDiagramRepository.save(ModelingExerciseFactory.generateApollonDiagram(DiagramType.ActivityDiagram, "activityDiagram1"));
        testCacheEvicted("diagramTitle", () -> new Tuple<>(apollonDiagram.getId(), apollonDiagram.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    apollonDiagram.setTitle("testEvictsTitleOnUpdateTitleOrDeleteApollonDiagram");
                    apollonDiagramRepository.save(apollonDiagram);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    apollonDiagram.setDiagramType(DiagramType.ClassDiagram); // Change some other values
                    apollonDiagramRepository.save(apollonDiagram);
                    return false;
                },
                // Should evict after deletion
                () -> {
                    apollonDiagramRepository.delete(apollonDiagram);
                    return true;
                }));
    }

    @Test
    void testEvictsTitleOnUpdateTitleOrDeleteExam() {
        var course = courseUtilService.createCourse();
        var exam = examUtilService.addExam(course);
        testCacheEvicted("examTitle", () -> new Tuple<>(exam.getId(), exam.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    exam.setTitle("testEvictsTitleOnUpdateTitleOrDeleteExam");
                    examRepository.save(exam);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    exam.setConfirmationEndText("testEvictsTitleOnUpdateTitleOrDeleteExam"); // Change some other values
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
    void testEvictsTitleOnUpdateTitleOrDeleteExerciseHint() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        programmingExerciseUtilService.addHintsToExercise(exercise);
        var hint = exercise.getExerciseHints().stream().findFirst().orElseThrow();
        testCacheEvicted("exerciseHintTitle", () -> new Tuple<>(exercise.getId() + "-" + hint.getId(), hint.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    hint.setTitle("testEvictsTitleOnUpdateTitleOrDeleteExerciseHint");
                    exerciseHintRepository.save(hint);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    hint.setDescription("testEvictsTitleOnUpdateTitleOrDeleteExerciseHint"); // Change some other values
                    exerciseHintRepository.save(hint);
                    return false;
                },
                // Should not do something if the exercise is missing
                () -> {
                    hint.setExercise(null);
                    hint.setTitle("testEvictsTitleOnUpdateTitleOrDeleteExerciseHint");
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
