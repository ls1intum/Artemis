package de.tum.cit.aet.artemis.lecture.api;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

/**
 * API for the videos the ingestion path transcribes, used by the ingestion coverage dashboard.
 * <p>
 * Kept apart from {@link LectureUnitRepositoryApi}, which sits on long startup dependency chains: the video source
 * resolver it needs would lengthen those chains for every user of that API.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
public class LectureVideoCoverageApi extends AbstractLectureApi {

    private final LectureUnitRepository lectureUnitRepository;

    private final VideoSourceResolverService videoSourceResolver;

    public LectureVideoCoverageApi(LectureUnitRepository lectureUnitRepository, VideoSourceResolverService videoSourceResolver) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.videoSourceResolver = videoSourceResolver;
    }

    /**
     * Returns the (courseId, unitId) pairs of the units in the given courses whose video the ingestion path transcribes: a video from a supported source
     * on a lecture that is not a tutorial lecture. A link to any other video page is never transcribed, so expecting a transcript for it would report the
     * unit missing permanently.
     *
     * @param courseIds the ids of the courses
     * @return the (courseId, unitId) pairs of units with a supported video source
     */
    public List<CourseEntityIdDTO> findUnitIdCourseIdPairsWithSupportedVideoForCourses(Collection<Long> courseIds) {
        return lectureUnitRepository.findVideoSourcesForCourses(courseIds).stream().filter(unit -> videoSourceResolver.isSupportedSource(unit.videoSource()))
                .map(unit -> new CourseEntityIdDTO(unit.courseId(), unit.unitId())).toList();
    }
}
