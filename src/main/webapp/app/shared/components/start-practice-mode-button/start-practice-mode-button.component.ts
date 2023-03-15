import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faRedo } from '@fortawesome/free-solid-svg-icons';
import { finalize } from 'rxjs/operators';

import { AlertService } from 'app/core/util/alert.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-start-practice-mode-button',
    templateUrl: './start-practice-mode-button.component.html',
    styleUrls: ['./start-practice-mode-button.component.scss'],
})
export class StartPracticeModeButtonComponent implements OnInit {
    readonly FeatureToggle = FeatureToggle;

    @Input()
    smallButtons: boolean;
    @Input()
    exercise: Exercise;
    @Output()
    practiceModeStarted = new EventEmitter();

    startingPracticeMode = false;
    gradedStudentParticipation?: StudentParticipation;

    // Icons
    faRedo = faRedo;

    constructor(private courseExerciseService: CourseExerciseService, private alertService: AlertService, private participationService: ParticipationService) {}

    ngOnInit() {
        this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations ?? [], false);
    }

    startPractice(useGradedParticipation: boolean): void {
        this.startingPracticeMode = true;
        this.courseExerciseService
            .startPractice(this.exercise.id!, useGradedParticipation)
            .pipe(finalize(() => (this.startingPracticeMode = false)))
            .subscribe({
                next: (participation) => {
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        if (participation?.initializationState === InitializationState.INITIALIZED) {
                            if ((this.exercise as ProgrammingExercise).allowOfflineIde) {
                                this.alertService.success('artemisApp.exercise.personalRepositoryClone');
                            } else {
                                this.alertService.success('artemisApp.exercise.personalRepositoryOnline');
                            }
                        } else {
                            this.alertService.error('artemisApp.exercise.startError');
                        }
                        if (participation) {
                            this.practiceModeStarted.emit(participation);
                        }
                    }
                },
                error: () => {
                    this.alertService.error('artemisApp.exercise.startError');
                },
            });
    }
}
