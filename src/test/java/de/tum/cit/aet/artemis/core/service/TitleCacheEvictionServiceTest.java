package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.core.organization.util.OrganizationUtilService;
import de.tum.cit.aet.artemis.core.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.Pair;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.repository.ApollonDiagramRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Test for {@link TitleCacheEvictionService} that should evict entity titles from the title caches if the titles are
 * updated, but leave them untouched if the titles don't change.
 * <p>
 * The service is not directly injected / used here as it listens to Hibernate events, so we just apply
 * CRUD operations on the entities it supports.
 */
class TitleCacheEvictionServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApollonDiagramRepository apollonDiagramRepository;

    @Autowired
    private ExamTestRepository examRepository;

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
    private ApplicationContext applicationContext;

    @BeforeEach
    void setup() {
        // trigger lazy bean initialization, so the PostConstruct method of TitleCacheEvictionService is called
        // in production, the DeferredEagerBeanInitializer would take care of this
        applicationContext.getBean(TitleCacheEvictionService.class);

    }

    @Test
    void testEvictsTitleOnUpdateTitleOrDeleteCourse() {
        var course = courseUtilService.addEmptyCourse();
        testCacheEvicted("courseTitle", () -> new Pair<>(course.getId(), course.getTitle()), List.of(
                // Should evict as we change the title
                () -> {
                    course.setTitle("testEvictsTitleOnUpdateTitleOrDeleteCourse");
                    courseRepository.save(course);
                    return true;
                },
                // Should not evict as title remains the same
                () -> {
                    course.getExtendedSettings().setDescription("testEvictsTitleOnUpdateTitleOrDeleteCourse"); // Change some other values
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
        testCacheEvicted("exerciseTitle", () -> new Pair<>(exercise.getId(), exercise.getTitle()), List.of(
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
        testCacheEvicted("lectureTitle", () -> new Pair<>(lecture.getId(), lecture.getTitle()), List.of(
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
        testCacheEvicted("organizationTitle", () -> new Pair<>(org.getId(), org.getName()), List.of(
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
        testCacheEvicted("diagramTitle", () -> new Pair<>(apollonDiagram.getId(), apollonDiagram.getTitle()), List.of(
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
        testCacheEvicted("examTitle", () -> new Pair<>(exam.getId(), exam.getTitle()), List.of(
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

    private void testCacheEvicted(String cacheName, Supplier<Pair<Object, String>> idTitleSupplier, List<Supplier<Boolean>> entityModifiers) {
        var cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();

        for (var modifier : entityModifiers) {
            var objInCache = idTitleSupplier.get();
            cache.put(objInCache.first(), objInCache.second());
            var cacheValueWrapper = cache.get(objInCache.first());
            assertThat(cacheValueWrapper).isNotNull();
            assertThat(cacheValueWrapper.get()).isEqualTo(objInCache.second());

            boolean shouldEvict = modifier.get();
            cacheValueWrapper = cache.get(objInCache.first());
            if (shouldEvict) {
                assertThat(cacheValueWrapper).isNull();
            }
            else {
                assertThat(cacheValueWrapper).isNotNull();
                assertThat(cacheValueWrapper.get()).isEqualTo(objInCache.second());
            }
        }
    }
}
