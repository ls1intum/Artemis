package de.tum.cit.aet.artemis.calendar.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.calendar.config.CalendarEnabled;
import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(CalendarEnabled.class)
@Repository
public interface CourseCalendarEventRepository extends ArtemisJpaRepository<CourseCalendarEvent, Long> {

    @Query("""
            SELECT e
            FROM CourseCalendarEvent e
                JOIN FETCH e.course c
            WHERE c.id IN :courseIds
            """)
    Set<CourseCalendarEvent> findAllByCourseIdsWithCourse(@Param("courseIds") Set<Long> courseIds);

    @Query("""
            SELECT e
            FROM CourseCalendarEvent e
                JOIN FETCH e.course
            WHERE e.id = :id
            """)
    Optional<CourseCalendarEvent> findByIdWithCourse(@Param("id") Long id);

    default CourseCalendarEvent findByIdWithCourseElseThrow(Long courseId) {
        return getValueElseThrow(findByIdWithCourse(courseId), courseId);
    }
}
