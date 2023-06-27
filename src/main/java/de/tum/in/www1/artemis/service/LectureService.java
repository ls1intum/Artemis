package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class LectureService {

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    public LectureService(LectureRepository lectureRepository, AuthorizationCheckService authCheckService) {
        this.lectureRepository = lectureRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * For tutors, admins and instructors returns lecture with all attachments, for students lecture with only active attachments
     *
     * @param lectureWithAttachments lecture that has attachments
     * @param user                   the user for which this call should filter
     * @return lecture with filtered attachments
     */
    public Lecture filterActiveAttachments(Lecture lectureWithAttachments, User user) {
        Course course = lectureWithAttachments.getCourse();
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return lectureWithAttachments;
        }

        HashSet<Attachment> filteredAttachments = new HashSet<>();
        for (Attachment attachment : lectureWithAttachments.getAttachments()) {
            if (attachment.getReleaseDate() == null || attachment.getReleaseDate().isBefore(ZonedDateTime.now())) {
                filteredAttachments.add(attachment);
            }
        }
        lectureWithAttachments.setAttachments(filteredAttachments);
        return lectureWithAttachments;
    }

    /**
     * Lecture with only active attachment units
     *
     * @param lectureWithAttachmentUnits lecture that has attachment units
     */
    public void filterActiveAttachmentUnits(Lecture lectureWithAttachmentUnits) {

        List<LectureUnit> filteredAttachmentUnits = new ArrayList<>();
        for (LectureUnit unit : lectureWithAttachmentUnits.getLectureUnits()) {
            if (unit instanceof AttachmentUnit && (((AttachmentUnit) unit).getAttachment().getReleaseDate() == null
                    || ((AttachmentUnit) unit).getAttachment().getReleaseDate().isBefore(ZonedDateTime.now()))) {
                filteredAttachmentUnits.add(unit);
            }
        }
        lectureWithAttachmentUnits.setLectureUnits(filteredAttachmentUnits);
    }

    /**
     * Filter active attachments for a set of lectures.
     *
     * @param lecturesWithAttachments lectures that have attachments
     * @param user                    the user for which this call should filter
     * @return lectures with filtered attachments
     */
    public Set<Lecture> filterActiveAttachments(Set<Lecture> lecturesWithAttachments, User user) {
        Set<Lecture> lecturesWithFilteredAttachments = new HashSet<>();
        for (Lecture lecture : lecturesWithAttachments) {
            lecturesWithFilteredAttachments.add(filterActiveAttachments(lecture, user));
        }
        return lecturesWithFilteredAttachments;
    }

    /**
     * Search for all lectures fitting a {@link PageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to fetch all available lectures
     * @return A wrapper object containing a list of all found lectures and the total number of pages
     */
    public SearchResultPageDTO<Lecture> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createLecturePageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<Lecture> lecturePage;
        if (authCheckService.isAdmin(user)) {
            lecturePage = lectureRepository.findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(searchTerm, searchTerm, pageable);
        }
        else {
            lecturePage = lectureRepository.findByTitleInLectureOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), pageable);
        }
        return new SearchResultPageDTO<>(lecturePage.getContent(), lecturePage.getTotalPages());
    }

    /**
     * Deletes the given lecture (with its lecture units).
     *
     * @param lecture the lecture to be deleted
     */
    public void delete(Lecture lecture) {
        lectureRepository.deleteById(lecture.getId());
    }

}
