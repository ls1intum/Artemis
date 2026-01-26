import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { finalize } from 'rxjs/operators';
import { faRedo } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

@Component({
    selector: 'jhi-start-practice-mode-button',
    templateUrl: './start-practice-mode-button.component.html',
    styleUrls: ['./start-practice-mode-button.component.scss'],
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, NgbPopover, TranslateDirective, ArtemisTranslatePipe],
})
export class StartPracticeModeButtonComponent {
    private courseExerciseService = inject(CourseExerciseService);
    private alertService = inject(AlertService);
    private participationService = inject(ParticipationService);

    readonly FeatureToggle = FeatureToggle;

    readonly smallButtons = input<boolean>(undefined!);
    readonly exercise = input<Exercise>(undefined!);
    readonly practiceModeStarted = output<StudentParticipation>();

    private readonly _startingPracticeMode = signal(false);
    private readonly _gradedStudentParticipation = signal<StudentParticipation | undefined>(undefined);

    readonly startingPracticeMode = computed(() => this._startingPracticeMode());
    readonly gradedStudentParticipation = computed(() => this._gradedStudentParticipation());

    // Icons
    faRedo = faRedo;

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            untracked(() => {
                this._gradedStudentParticipation.set(this.participationService.getSpecificStudentParticipation(exercise.studentParticipations ?? [], false));
            });
        });
    }

    startPractice(useGradedParticipation: boolean): void {
        this._startingPracticeMode.set(true);
        this.courseExerciseService
            .startPractice(this.exercise().id!, useGradedParticipation)
            .pipe(finalize(() => this._startingPracticeMode.set(false)))
            .subscribe({
                next: (participation) => {
                    const exercise = this.exercise();
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        if (participation?.initializationState === InitializationState.INITIALIZED) {
                            if ((exercise as ProgrammingExercise).allowOfflineIde) {
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
