package de.tum.in.www1.artemis.web.rest.iris;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.connectors.iris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.iris.job.PyrisJob;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * REST controller for providing Pyris access to Artemis internal data
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/pipelines/data/")
public class PyrisDataResource {

    private final PyrisJobService pyrisJobService;

    private final LectureUnitRepository lectureUnitRepository;

    public PyrisDataResource(PyrisJobService pyrisJobService, LectureUnitRepository lectureUnitRepository) {
        this.pyrisJobService = pyrisJobService;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    @GetMapping("lecture-units/{lectureUnitId}/pdf")
    @EnforceNothing // We do token based authentication
    public ModelAndView downloadLectureUnitPDF(@PathVariable Long lectureUnitId, HttpServletRequest request) {
        // TODO: Token auth
        var job = getJobFromHeader(request);

        var lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);

        if (!job.canAccess(lectureUnit)) {
            throw new AccessForbiddenException("You are not allowed to access this lecture unit");
        }

        if (!(lectureUnit instanceof AttachmentUnit attachmentUnit)) {
            throw new BadRequestException("This lecture unit does not have an attachment");
        }

        return new ModelAndView("forward:" + attachmentUnit.getAttachment().getLink());
    }

    private PyrisJob getJobFromHeader(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (!authHeader.startsWith("Bearer ")) {
            throw new AccessForbiddenException("No valid token provided");
        }
        var token = authHeader.substring(7);
        var job = pyrisJobService.getJob(token);
        if (job == null) {
            throw new AccessForbiddenException("No valid token provided");
        }
        return job;
    }
}
