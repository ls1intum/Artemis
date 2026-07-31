import { ChangeDetectionStrategy, Component, effect, inject, input, model, signal, untracked } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';

@Component({
    selector: 'jhi-consistency-check',
    templateUrl: './consistency-check.component.html',
    imports: [TranslateDirective, FaIconComponent, RouterLink, ArtemisTranslatePipe, TumUiDialogComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsistencyCheckComponent {
    private consistencyCheckService = inject(ConsistencyCheckService);
    private alertService = inject(AlertService);

    /** Two-way visibility, driven by the parent. */
    readonly visible = model<boolean>(false);
    /** The programming exercises whose consistency should be checked. */
    readonly exercisesToCheck = input<ProgrammingExercise[]>([]);

    readonly inconsistencies = signal<ConsistencyCheckError[]>([]);
    readonly isLoading = signal(true);

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;

    constructor() {
        // Run the check on each open, not just once in ngOnInit; untracked so a mid-open change doesn't re-trigger.
        effect(() => {
            if (this.visible()) {
                untracked(() => this.runCheck());
            }
        });
    }

    private runCheck(): void {
        this.inconsistencies.set([]);
        this.isLoading.set(true);
        const exercisesToCheck = this.exercisesToCheck();
        if (exercisesToCheck.length === 0) {
            this.isLoading.set(false);
            return;
        }
        let exercisesRemaining = exercisesToCheck.length;
        exercisesToCheck.forEach((exercise) => {
            const course = getCourseId(exercise);
            this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
                next: (inconsistencies) => {
                    const updatedInconsistencies = this.inconsistencies().concat(inconsistencies);
                    updatedInconsistencies.forEach((inconsistency) => (inconsistency.programmingExerciseCourseId = course || undefined));
                    this.inconsistencies.set(updatedInconsistencies);
                    if (--exercisesRemaining === 0) {
                        this.isLoading.set(false);
                    }
                },
                error: (err) => {
                    this.alertService.error(err);
                    this.isLoading.set(false);
                },
            });
        });
    }

    closeModal() {
        this.visible.set(false);
    }
}
