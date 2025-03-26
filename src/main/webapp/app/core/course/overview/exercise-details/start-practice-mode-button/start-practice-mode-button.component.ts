import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { finalize } from 'rxjs/operators';
import { faRedo } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { InitializationState } from 'app/entities/participation/participation.model';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

@Component({
    selector: 'jhi-start-practice-mode-button',
    templateUrl: './start-practice-mode-button.component.html',
    styleUrls: ['./start-practice-mode-button.component.scss'],
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, NgbPopover, TranslateDirective, ArtemisTranslatePipe],
})
export class StartPracticeModeButtonComponent implements OnInit {
    private courseExerciseService = inject(CourseExerciseService);
    private alertService = inject(AlertService);
    private participationService = inject(ParticipationService);

    readonly FeatureToggle = FeatureToggle;

    @Input() smallButtons: boolean;
    @Input() exercise: Exercise;
    @Output() practiceModeStarted = new EventEmitter();

    startingPracticeMode = false;
    gradedStudentParticipation?: StudentParticipation;

    // Icons
    faRedo = faRedo;

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
