package de.tum.in.www1.artemis.service;

import javax.persistence.EntityManagerFactory;

import org.apache.commons.lang3.ArrayUtils;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;

/**
 * Listens to Hibernate events and invalidates the cached titles of an entity if the title changed.
 * This is used in endpoints that return only the title of an entity which are consumed by breadcrumbs in the client.
 */
@Service
public class TitleCacheEvictionService implements PostUpdateEventListener, PostDeleteEventListener {

    private final Logger log = LoggerFactory.getLogger(TitleCacheEvictionService.class);

    private final CacheManager cacheManager;

    public TitleCacheEvictionService(EntityManagerFactory entityManagerFactory, CacheManager cacheManager) {
        this.cacheManager = cacheManager;

        var eventListenerRegistry = entityManagerFactory.unwrap(SessionFactoryImpl.class).getServiceRegistry().getService(EventListenerRegistry.class);
        eventListenerRegistry.appendListeners(EventType.POST_UPDATE, this);
        eventListenerRegistry.appendListeners(EventType.POST_DELETE, this);
        log.info("Registered Hibernate listeners");
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        // On delete, we can the evict the title in all cases - we won't need it anymore
        evictEntityTitle(event.getEntity());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        // Check if the title / name property is marked as "dirty" (= changed somehow) by Hibernate.
        // If yes, evict the title from the cache
        var titlePropertyName = event.getEntity() instanceof Organization ? "name" : "title";
        int propertyIndex = ArrayUtils.indexOf(event.getPersister().getPropertyNames(), titlePropertyName);
        if (propertyIndex < 0 || !ArrayUtils.contains(event.getDirtyProperties(), propertyIndex)) {
            return;
        }
        evictEntityTitle(event.getEntity());
    }

    /**
     * Evict the title of the given entity
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
    }

    /**
     * Removes the given id from the given title cache
     * @param cacheName the title cache to evict the entry from
     * @param entityId the entry to evict from
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
    public boolean requiresPostCommitHanding(EntityPersister persister) {
        return false;
    }
}
