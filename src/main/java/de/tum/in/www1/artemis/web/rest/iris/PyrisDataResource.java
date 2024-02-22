package de.tum.in.www1.artemis.web.rest.iris;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.EXPORT_INSTRUCTOR_AUXILIARY_REPOSITORY;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.EXPORT_INSTRUCTOR_REPOSITORY;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.EXPORT_SUBMISSION_OF_PARTICIPATION;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.InternalResourceView;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;
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

    private final ExerciseRepository exerciseRepository;

    public PyrisDataResource(PyrisJobService pyrisJobService, LectureUnitRepository lectureUnitRepository, ExerciseRepository exerciseRepository) {
        this.pyrisJobService = pyrisJobService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @GetMapping("programming-exercises/{exerciseId}/repositories/{repositoryType}")
    @EnforceNothing // We do token based authentication
    public ModelAndView getInstructorRepository(@PathVariable long exerciseId, @PathVariable RepositoryType repositoryType, HttpServletRequest request) {
        var job = getJobFromHeader(request);

        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (!job.canAccess(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this repository");
        }

        SecurityUtils.setAuthorizationObject();
        var path = ROOT + EXPORT_INSTRUCTOR_REPOSITORY.replace("{exerciseId}", String.valueOf(exerciseId)).replace("{repositoryType}", repositoryType.getName());
        return new ModelAndView(new InternalResourceView("forward:" + path));
    }

    @GetMapping("programming-exercises/{exerciseId}/repositories/auxiliary/{repositoryId}")
    @EnforceNothing // We do token based authentication
    public ModelAndView getAuxRepository(@PathVariable long exerciseId, @PathVariable long repositoryId, HttpServletRequest request) {
        var job = getJobFromHeader(request);

        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (!job.canAccess(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this repository");
        }

        SecurityUtils.setAuthorizationObject();
        var path = ROOT + EXPORT_INSTRUCTOR_AUXILIARY_REPOSITORY.replace("{exerciseId}", String.valueOf(exerciseId)).replace("{repositoryId}", String.valueOf(repositoryId));
        return new ModelAndView("forward:" + path);
    }

    @GetMapping("programming-exercises/{exerciseId}/repositories/participation/{participationId}")
    @EnforceNothing // We do token based authentication
    public ModelAndView getUserRepository(@PathVariable long exerciseId, @PathVariable long participationId, HttpServletRequest request) {
        var job = getJobFromHeader(request);

        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (!job.canAccess(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this repository");
        }

        SecurityUtils.setAuthorizationObject();
        var path = ROOT + EXPORT_SUBMISSION_OF_PARTICIPATION.replace("{exerciseId}", String.valueOf(exerciseId)).replace("{participationId}", String.valueOf(participationId));
        return new ModelAndView("forward:" + path);
    }

    @GetMapping("lecture-units/{lectureUnitId}/pdf")
    @EnforceNothing // We do token based authentication
    public ModelAndView downloadLectureUnitPDF(@PathVariable Long lectureUnitId, HttpServletRequest request) {
        var job = getJobFromHeader(request);

        var lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);

        if (!job.canAccess(lectureUnit)) {
            throw new AccessForbiddenException("You are not allowed to access this lecture unit");
        }

        if (!(lectureUnit instanceof AttachmentUnit attachmentUnit)) {
            throw new BadRequestException("This lecture unit does not have an attachment");
        }

        SecurityUtils.setAuthorizationObject();
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
