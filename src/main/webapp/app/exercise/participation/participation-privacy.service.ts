import { Service, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { interval, map } from 'rxjs';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

/** Mirrors the server's participation identity policy; authorization is enforced on every server request. */
@Service()
export class ParticipationPrivacyService {
    private readonly serverDateService = inject(ArtemisServerDateService);
    private readonly now = toSignal(interval(1000).pipe(map(() => this.serverDateService.now())), { initialValue: this.serverDateService.now() });

    /**
     * Hides identities until an exercise is available and throughout exam assessment for non-instructors.
     * Course programming exercises expose identities until the manual/semi-automatic assessment due date;
     * automatic assessment and exercises without a due date retain that visibility. Uses server time so an
     * already-open page updates at the cutoff. This display policy never grants server repository access.
     * @param exercise the currently loaded exercise, or undefined while loading
     * @returns whether participant names and repository links must be hidden
     */
    isParticipantInformationHidden(exercise: Exercise | undefined): boolean {
        if (!exercise) return true;
        if (exercise.isAtLeastInstructor) return false;
        if (exercise.type !== ExerciseType.PROGRAMMING || exercise.exerciseGroup) return true;
        return this.hasManualAssessmentDeadline(exercise) && !this.now().isBefore(exercise.dueDate);
    }

    /**
     * Identifies assessment modes that require anonymous tutor assessment after the due date.
     * @param exercise the exercise whose assessment mode is checked
     * @returns whether assessment is manual or semi-automatic
     */
    hasManualAssessment(exercise: Exercise): boolean {
        return exercise.assessmentType === AssessmentType.MANUAL || exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC;
    }

    /**
     * Checks whether manual or semi-automatic assessment has a configured due date.
     * The caller applies this deadline within the course programming-exercise privacy policy.
     * @param exercise the exercise whose mode and due date are checked
     * @returns whether manual or semi-automatic assessment has a due date
     */
    hasManualAssessmentDeadline(exercise: Exercise): boolean {
        return !!exercise.dueDate && this.hasManualAssessment(exercise);
    }
}
