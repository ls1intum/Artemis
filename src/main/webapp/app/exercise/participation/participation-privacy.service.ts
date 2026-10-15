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

    isParticipantInformationHidden(exercise: Exercise | undefined): boolean {
        if (!exercise) return true;
        if (exercise.isAtLeastInstructor) return false;
        if (exercise.type !== ExerciseType.PROGRAMMING || exercise.exerciseGroup) return true;
        return this.hasManualAssessmentDeadline(exercise) && !this.now().isBefore(exercise.dueDate);
    }

    hasManualAssessment(exercise: Exercise): boolean {
        return exercise.assessmentType === AssessmentType.MANUAL || exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC;
    }

    hasManualAssessmentDeadline(exercise: Exercise): boolean {
        return !!exercise.dueDate && this.hasManualAssessment(exercise);
    }
}
