import { Component, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faBackward } from '@fortawesome/free-solid-svg-icons';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { finalize } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';
import dayjs from 'dayjs/esm';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-reset-repo-button',
    templateUrl: './reset-repo-button.component.html',
    styleUrls: ['./reset-repo-button.component.scss'],
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, NgbPopover, FormsModule, TranslateDirective, ConfirmEntityNameComponent, ArtemisTranslatePipe],
})
export class ResetRepoButtonComponent {
    private participationService = inject(ParticipationService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private alertService = inject(AlertService);

    readonly FeatureToggle = FeatureToggle;
    readonly INITIALIZED = InitializationState.INITIALIZED;

    readonly exercise = input<ProgrammingExercise>(undefined!);
    readonly participations = input<StudentParticipation[]>(undefined!);
    readonly smallButtons = input<boolean>(undefined!);

    readonly popover = viewChild.required<NgbPopover>('popover');

    private readonly _gradedParticipation = signal<StudentParticipation | undefined>(undefined);
    private readonly _practiceParticipation = signal<StudentParticipation | undefined>(undefined);
    private readonly _beforeIndividualDueDate = signal(true);
    private readonly _isLoading = signal(false);

    readonly gradedParticipation = computed(() => this._gradedParticipation());
    readonly practiceParticipation = computed(() => this._practiceParticipation());
    readonly beforeIndividualDueDate = computed(() => this._beforeIndividualDueDate());
    readonly isLoading = computed(() => this._isLoading());

    readonly faBackward = faBackward;

    constructor() {
        effect(() => {
            const participations = this.participations();
            const exercise = this.exercise();
            untracked(() => {
                const gradedParticipation = this.participationService.getSpecificStudentParticipation(participations, false);
                this._gradedParticipation.set(gradedParticipation);
                this._practiceParticipation.set(this.participationService.getSpecificStudentParticipation(participations, true));
                const individualDueDate = getExerciseDueDate(exercise, gradedParticipation);
                this._beforeIndividualDueDate.set(!individualDueDate || dayjs().isBefore(individualDueDate));
            });
        });
    }

    resetRepository(gradedParticipationId?: number) {
        this._isLoading.set(true);
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        const participationId = this._practiceParticipation()?.id ?? this._gradedParticipation()?.id!;
        this.programmingExerciseParticipationService
            .resetRepository(participationId, gradedParticipationId)
            .pipe(
                finalize(() => {
                    this._isLoading.set(false);
                    this.popover().close();
                }),
            )
            .subscribe({
                next: () => {
                    this.alertService.success('artemisApp.exerciseActions.resetRepository.success');
                    window.scrollTo(0, 0);
                },
            });
    }
}
