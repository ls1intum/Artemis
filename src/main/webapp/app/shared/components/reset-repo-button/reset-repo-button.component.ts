import { Component, Input, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faBackward } from '@fortawesome/free-solid-svg-icons';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { InitializationState } from 'app/entities/participation/participation.model';
import { isStartExerciseAvailable, isStartPracticeAvailable } from 'app/exercises/shared/exercise/exercise.utils';
import { finalize } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-reset-repo-button',
    templateUrl: './reset-repo-button.component.html',
    styleUrls: ['./reset-repo-button.component.scss'],
})
export class ResetRepoButtonComponent implements OnInit {
    readonly FeatureToggle = FeatureToggle;
    readonly INITIALIZED = InitializationState.INITIALIZED;

    @Input() exercise: ProgrammingExercise;
    @Input() participations: StudentParticipation[];
    @Input() smallButtons: boolean;

    gradedParticipation?: StudentParticipation;
    practiceParticipation?: StudentParticipation;

    confirmEntityName: string;

    // Icons
    faBackward = faBackward;

    constructor(
        private participationService: ParticipationService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        this.gradedParticipation = this.participationService.getSpecificStudentParticipation(this.participations, false);
        this.practiceParticipation = this.participationService.getSpecificStudentParticipation(this.participations, true);
    }

    /**
     * see exercise.utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    /**
     *  see exercise.utils -> isStartPracticeAvailable
     */
    isStartPracticeAvailable(): boolean {
        return isStartPracticeAvailable(this.exercise as ProgrammingExercise);
    }

    resetRepository(gradedParticipationId?: number) {
        this.exercise.loading = true;
        const participationId = this.practiceParticipation?.id ?? this.gradedParticipation?.id!;
        this.programmingExerciseParticipationService
            .resetRepository(participationId, gradedParticipationId)
            .pipe(
                finalize(() => {
                    this.exercise.loading = false;
                    this.alertService.success('artemisApp.exerciseActions.resetRepository.success');
                    window.scrollTo(0, 0);
                }),
            )
            .subscribe();
    }
}
