package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;

@Service
public class EntityTitleCacheService {

    private final ApollonDiagramRepository apollonDiagramRepository;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureRepository lectureRepository;

    private final OrganizationRepository organizationRepository;

    private final ExerciseHintRepository exerciseHintRepository;

    private final transient IMap<String, String> titleMap;

    public static final String HAZELCAST_TITLE_CACHE = "entity-title-cache";

    public EntityTitleCacheService(ExerciseRepository exerciseRepository, ApollonDiagramRepository apollonDiagramRepository, CourseRepository courseRepository,
            ExamRepository examRepository, LectureRepository lectureRepository, OrganizationRepository organizationRepository, ExerciseHintRepository exerciseHintRepository,
            HazelcastInstance hazelcastInstance) {
        this.exerciseRepository = exerciseRepository;
        this.apollonDiagramRepository = apollonDiagramRepository;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.lectureRepository = lectureRepository;
        this.organizationRepository = organizationRepository;
        this.exerciseHintRepository = exerciseHintRepository;
        this.titleMap = hazelcastInstance.getMap(HAZELCAST_TITLE_CACHE);
    }

    public String getDiagramTitle(Long diagramID) {
        return titleMap.computeIfAbsent("diagram" + diagramID, ignored -> apollonDiagramRepository.getDiagramTitle(diagramID));
    }

    public String getCourseTitle(Long courseID) {
        return titleMap.computeIfAbsent("course" + courseID, ignored -> courseRepository.getCourseTitle(courseID));
    }

    public String getExamTitle(Long examID) {
        return titleMap.computeIfAbsent("exam" + examID, ignored -> examRepository.getExamTitle(examID));
    }

    public String getExerciseTitle(Long exerciseId) {
        return titleMap.computeIfAbsent("exercise" + exerciseId, ignored -> exerciseRepository.getExerciseTitle(exerciseId));
    }

    public String getLectureTitle(Long lectureId) {
        return titleMap.computeIfAbsent("lecture" + lectureId, ignored -> lectureRepository.getLectureTitle(lectureId));
    }

    public String getOrganizationTitle(Long organizationId) {
        return titleMap.computeIfAbsent("organization" + organizationId, ignored -> organizationRepository.getOrganizationTitle(organizationId));
    }

    public String getHintTitle(Long hintId) {
        return titleMap.computeIfAbsent("hint" + hintId, ignored -> exerciseHintRepository.getHintTitle(hintId));
    }

    /**
     * Configures Hazelcast for the EntityTitleCache before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the EntityTitleCache-specific configuration should be added to
     */
    public static void configureHazelcast(Config config) {
        EvictionConfig evictionConfig = new EvictionConfig() //
                .setEvictionPolicy(EvictionPolicy.NONE);
        NearCacheConfig nearCacheConfig = new NearCacheConfig() //
                .setName(HAZELCAST_TITLE_CACHE + "-local") //
                .setInMemoryFormat(InMemoryFormat.OBJECT) //
                .setSerializeKeys(true) //
                .setInvalidateOnChange(true) //
                .setTimeToLiveSeconds(0) //
                .setMaxIdleSeconds(0) //
                .setEvictionConfig(evictionConfig) //
                .setCacheLocalEntries(true);
        config.getMapConfig(HAZELCAST_TITLE_CACHE).setNearCacheConfig(nearCacheConfig);
    }
}
