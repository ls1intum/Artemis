package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.jsoup.Jsoup;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeCommunicationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.communication.repository.CourseHomeCommunicationRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeCommunicationService {

    private final CourseHomeCommunicationRepository repository;

    private final CourseRepository courses;

    private final AuthorizationCheckService authorization;

    public CourseHomeCommunicationService(CourseHomeCommunicationRepository repository, CourseRepository courses, AuthorizationCheckService authorization) {
        this.repository = repository;
        this.courses = courses;
        this.authorization = authorization;
    }

    public CourseHomeCommunicationDTO load(long courseId, User user, Instant asOf, Instant since) {
        if (!authorization.isAtLeastStudentInCourse(courseId))
            throw new AccessForbiddenException("course", courseId);
        var now = ZonedDateTime.ofInstant(asOf, ZoneOffset.UTC);
        var baseline = ZonedDateTime.ofInstant(since, ZoneOffset.UTC);
        boolean enabled = courses.isCommunicationEnabled(courseId);
        var questions = enabled ? repository.findQuestions(courseId, user.getId(), now, PageRequest.of(0, 3)).stream()
                .map(row -> new CourseHomeQuestionsDTO.Item(row.getId(), row.getConversationId(), plainTitle(row.getTitle(), row.getExcerpt()), row.getReplies(),
                        row.getResolved() ? CourseHomeQuestionsDTO.State.RESOLVED
                                : row.getExternalReplies() > 0 ? CourseHomeQuestionsDTO.State.ANSWERED : CourseHomeQuestionsDTO.State.AWAITING_REPLY,
                        row.getUnread(), row.getActivityAt().toInstant(), row.getFocusReplyId()))
                .toList() : List.<CourseHomeQuestionsDTO.Item>of();
        var announcement = enabled ? repository.findAnnouncements(courseId, user.getId(), now, baseline, PageRequest.of(0, 1)).stream()
                .map(row -> new CourseHomeDTO.Item(plainTitle(row.getTitle(), row.getExcerpt()),
                        new CourseHomeTargetDTO(CourseHomeTargetDTO.Kind.THREAD, row.getId(), row.getConversationId(), null), row.getActivityAt().toInstant(), null,
                        "IMPORTANT_ANNOUNCEMENT"))
                .findFirst().orElse(null) : null;
        var faqs = repository.findFaqs(courseId, asOf, since, PageRequest.of(0, 2)).stream().map(row -> new CourseHomeDTO.Item(plainTitle(row.getTitle(), ""),
                new CourseHomeTargetDTO(CourseHomeTargetDTO.Kind.FAQ, row.getId(), null, null), row.getActivityAt(), null, "NEW_FAQ")).toList();
        return new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(enabled ? CourseHomeDTO.SectionStatus.AVAILABLE : CourseHomeDTO.SectionStatus.DISABLED, questions),
                announcement, faqs);
    }

    private String plainTitle(String title, String excerpt) {
        String value = title == null || title.isBlank() ? excerpt : title;
        String plain = Jsoup.parse(value == null ? "" : value).text().strip();
        return plain.length() > 160 ? plain.substring(0, 159) + "…" : plain;
    }
}
