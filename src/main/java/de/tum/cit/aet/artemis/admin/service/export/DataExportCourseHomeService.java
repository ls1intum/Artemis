package de.tum.cit.aet.artemis.admin.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.course.dto.home.CourseHomeVisitExportDTO;
import de.tum.cit.aet.artemis.course.repository.CourseHomeVisitRepository;

/** Includes the requesting student's course Home visit data in their personal export. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class DataExportCourseHomeService {

    private final CourseHomeVisitRepository visits;

    private final JsonMapper mapper;

    public DataExportCourseHomeService(CourseHomeVisitRepository visits, JsonMapper mapper) {
        this.visits = visits;
        this.mapper = mapper;
    }

    public void createCourseHomeExport(long userId, Path workingDirectory) throws IOException {
        var rows = visits.findAllByUserIdOrderByCourseId(userId).stream()
                .map(visit -> new CourseHomeVisitExportDTO(visit.getCourseId(), visit.getFirstSeenAt(), visit.getLastSeenAt(), visit.getVariantCursor())).toList();
        mapper.writerWithDefaultPrettyPrinter().writeValue(workingDirectory.resolve("course_home_visits.json").toFile(), rows);
    }
}
