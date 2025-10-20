package de.tum.cit.aet.artemis.quiz.web;

import java.security.Principal;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.config.AiFeatureFlagConfiguration;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.service.ai.AiQuizGenerationService;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationResponseDTO;

// src/main/java/de/tum/cit/aet/artemis/quiz/web/AiQuizGenerationResource.java
@RestController
@RequestMapping("api/courses/{courseId}/ai/quiz")
public class AiQuizGenerationResource {

    private final AiFeatureFlagConfiguration flags;

    private final AiQuizGenerationService service;

    private final AuthorizationCheckService authCheck;

    public AiQuizGenerationResource(AiFeatureFlagConfiguration flags, AiQuizGenerationService service, AuthorizationCheckService authCheck) {
        this.flags = flags;
        this.service = service;
        this.authCheck = authCheck;
    }

    @PostMapping("/generate")
    public ResponseEntity<AiQuizGenerationResponseDTO> generate(@PathVariable Long courseId, @Valid @RequestBody AiQuizGenerationRequestDTO req, Principal principal) {

        if (!flags.isEnabled())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI quiz generation is disabled");

        if (!(authCheck.isAdmin() || authCheck.isAtLeastEditorInCourse(principal.getName(), courseId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to generate AI questions for this course.");
        }

        var resp = service.generate(courseId, req, principal.getName());
        return ResponseEntity.ok(resp);
    }
}
