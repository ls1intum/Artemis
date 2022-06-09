package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.HAZELCAST_QUIZ_PREFIX;

import org.springframework.stereotype.Service;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.repository.ExerciseRepository;

@Service
public class EntityTitleService {

    private final ExerciseRepository exerciseRepository;

    private transient IMap<Long, String> exerciseTitles;

    public static final String HAZELCAST_EXERCISE_TITLE_CACHE = HAZELCAST_QUIZ_PREFIX + "exercise-cache";

    public EntityTitleService(ExerciseRepository exerciseRepository, HazelcastInstance hazelcastInstance) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseTitles = hazelcastInstance.getMap(HAZELCAST_EXERCISE_TITLE_CACHE);
    }

    public String getExerciseTitle(Long exerciseId) {
        return exerciseTitles.computeIfAbsent(exerciseId, exerciseRepository::getExerciseTitle);
    }

    public static void configureHazelcast(Config config) {
        EvictionConfig evictionConfig = new EvictionConfig() //
                .setEvictionPolicy(EvictionPolicy.NONE);
        NearCacheConfig nearCacheConfig = new NearCacheConfig() //
                .setName(HAZELCAST_EXERCISE_TITLE_CACHE + "-local") //
                .setInMemoryFormat(InMemoryFormat.OBJECT) //
                .setSerializeKeys(true) //
                .setInvalidateOnChange(true) //
                .setTimeToLiveSeconds(0) //
                .setMaxIdleSeconds(0) //
                .setEvictionConfig(evictionConfig) //
                .setCacheLocalEntries(true);
        config.getMapConfig(HAZELCAST_EXERCISE_TITLE_CACHE).setNearCacheConfig(nearCacheConfig);
    }
}
