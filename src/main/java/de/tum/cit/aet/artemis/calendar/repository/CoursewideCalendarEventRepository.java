package de.tum.cit.aet.artemis.calendar.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.calendar.config.CalendarEnabled;
import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Lazy
@Conditional(CalendarEnabled.class)
@Repository
public interface CoursewideCalendarEventRepository extends ArtemisJpaRepository<CoursewideCalendarEvent, Long> {

    @Query("""
            SELECT event
            FROM CoursewideCalendarEvent event
                JOIN event.course course
            WHERE course.id IN :courseIds
            """)
    Set<CoursewideCalendarEvent> findAllByCourseIdsWithCourse(@Param("courseIds") Set<Long> courseIds);

    @Query("""
            SELECT event
            FROM CoursewideCalendarEvent event
                JOIN event.course
            WHERE event.id = :id
            """)
    Optional<CoursewideCalendarEvent> findByIdWithCourse(@Param("id") Long id);

    default CoursewideCalendarEvent findByIdWithCourseElseThrow(Long id) {
        return getValueElseThrow(findByIdWithCourse(id), id);
    }
}
