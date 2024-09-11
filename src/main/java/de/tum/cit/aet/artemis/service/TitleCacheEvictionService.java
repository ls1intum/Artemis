package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.persistence.EntityManagerFactory;

import org.apache.commons.lang3.ArrayUtils;
import org.hibernate.Hibernate;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.modeling.domain.ApollonDiagram;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHint;

/**
 * Listens to Hibernate events and invalidates the cached titles of an entity if the title changed.
 * This is used in endpoints that return only the title of an entity which are consumed by breadcrumbs in the client.
 */
@Profile(PROFILE_CORE)
@Service
public class TitleCacheEvictionService implements PostUpdateEventListener, PostDeleteEventListener {

    private static final Logger log = LoggerFactory.getLogger(TitleCacheEvictionService.class);

    private final CacheManager cacheManager;

    private final EntityManagerFactory entityManagerFactory;

    public TitleCacheEvictionService(EntityManagerFactory entityManagerFactory, CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Registers Hibernate event listeners for POST_UPDATE and POST_DELETE events when the application is ready.
     *
     * <p>
     * If the {@link EventListenerRegistry} is available, the listeners are appended and a debug message is logged.
     * If the registry is null, a warning is logged indicating a possible misconfiguration.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        var eventListenerRegistry = entityManagerFactory.unwrap(SessionFactoryImpl.class).getServiceRegistry().getService(EventListenerRegistry.class);
        if (eventListenerRegistry != null) {
            eventListenerRegistry.appendListeners(EventType.POST_UPDATE, this);
            eventListenerRegistry.appendListeners(EventType.POST_DELETE, this);
            log.debug("Registered Hibernate listeners");
        }
        else {
            log.warn("Could not register Hibernate listeners because the EventListenerRegistry is null. This is likely due to a misconfiguration of the entity manager factory.");
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        // On delete, we can evict the title in all cases - we won't need it anymore
        evictEntityTitle(event.getEntity());
    }

    /**
     * Checks if the title / name property is marked as "dirty" (= changed somehow) by Hibernate.
     * If yes, evicts the title from the cache
     *
     * @param event the Hibernate update event
     */
    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        var titlePropertyName = "title";

        // special case for organizations
        if (event.getEntity() instanceof Organization) {
            titlePropertyName = "name";
        }

        int propertyIndex = ArrayUtils.indexOf(event.getPersister().getPropertyNames(), titlePropertyName);
        if (propertyIndex >= 0 && ArrayUtils.contains(event.getDirtyProperties(), propertyIndex)) {
            evictEntityTitle(event.getEntity());
        }
    }

    /**
     * Evict the title of the given entity
     *
     * @param entity the entity which title should be evicted
     */
    private void evictEntityTitle(Object entity) {
        if (entity instanceof Exercise exercise) {
            evictIdFromCache("exerciseTitle", exercise.getId());
        }
        else if (entity instanceof Course course) {
            evictIdFromCache("courseTitle", course.getId());
        }
        else if (entity instanceof Lecture lecture) {
            evictIdFromCache("lectureTitle", lecture.getId());
        }
        else if (entity instanceof Organization organization) {
            evictIdFromCache("organizationTitle", organization.getId());
        }
        else if (entity instanceof ApollonDiagram diagram) {
            evictIdFromCache("diagramTitle", diagram.getId());
        }
        else if (entity instanceof Exam exam) {
            evictIdFromCache("examTitle", exam.getId());
        }
        else if (entity instanceof ExerciseHint hint) {
            if (hint.getExercise() == null) {
                log.warn("Unable to clear title of exercise hint {}: Exercise not present", hint.getId());
                return;
            }

            var combinedId = hint.getExercise().getId() + "-" + hint.getId();
            evictIdFromCache("exerciseHintTitle", combinedId);
        }
        else if (entity instanceof ExerciseGroup exerciseGroup) {
            if (!Hibernate.isInitialized(exerciseGroup.getExercises())) {
                log.warn("Unable to clear title of exercises from exercise group {}: Exercises not initialized", exerciseGroup.getId());
                return;
            }
            var exercises = exerciseGroup.getExercises();
            for (Exercise exercise : exercises) {
                evictIdFromCache("exerciseTitle", exercise.getId());
            }
        }
    }

    /**
     * Removes the given id from the given title cache
     *
     * @param cacheName the title cache to evict the entry from
     * @param entityId  the entry to evict from
     */
    private void evictIdFromCache(String cacheName, Object entityId) {
        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Unable to evict entry in title cache: Cache {} not found", cacheName);
            return;
        }

        cache.evict(entityId);
        log.info("Evicted entry '{}' from title cache '{}'", entityId, cacheName);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }
}
