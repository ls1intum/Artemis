package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.exercise.domain.PlannedExercise;
import de.tum.cit.aet.artemis.exercise.dto.PlannedExerciseCreateDTO;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/planned-exercise/")
public class PlannedExerciseResource {

    @PostMapping("courses/{courseId}/create-all")
    @EnforceAtLeastEditor
    public ResponseEntity<List<PlannedExercise>> createAll(@PathVariable Long courseId, @RequestBody List<PlannedExerciseCreateDTO> dtos) {
    }

    @PostMapping("courses/{courseId}/create")
    @EnforceAtLeastEditor
    public ResponseEntity<PlannedExercise> create(@PathVariable Long courseId, @RequestBody PlannedExerciseCreateDTO fto) {
    }

    @GetMapping("courses/{courseId}/get-all")
    @EnforceAtLeastTutor
    public ResponseEntity<List<PlannedExercise>> getAll(@PathVariable Long courseId) {
    }

    @PutMapping("courses/{courseId}/update")
    @EnforceAtLeastEditor
    public ResponseEntity<List<PlannedExercise>> update(@PathVariable Long courseId) {
    }

    @DeleteMapping("courses/{courseId}/planned-exercises/{plannedExerciseId}/delete")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> delete(@PathVariable Long courseId, @PathVariable Long plannedExerciseId) {
    }
}
