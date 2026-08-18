import { ChangeDetectionStrategy, Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { ProgrammingExerciseTimelineComponent } from '../../shared/programming-exercise-update-timeline/programming-exercise-timeline.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { TumUiDialogComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-programming-exercise-edit-selected',
    templateUrl: './programming-exercise-edit-selected.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, FormsModule, ProgrammingExerciseTimelineComponent, ButtonComponent, FaIconComponent, TumUiDialogComponent],
    providers: [BuildPhasesTemplateService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseEditSelectedComponent {
    private translateService = inject(TranslateService);
    private programmingExerciseService = inject(ProgrammingExerciseService);

    /** Two-way visibility, driven by the parent. */
    readonly visible = model<boolean>(false);
    /** The programming exercises whose shared timeline is edited, supplied by the parent. */
    readonly selectedProgrammingExercises = input<ProgrammingExercise[]>([]);
    /** Emitted once all exercises saved successfully, so the parent can reload the list. */
    readonly saved = output<void>();

    // eslint-disable-next-line localRules/prefer-signal-template-state -- backs deep [(x)] two-way targets (e.g. [(releaseDate)]="newProgrammingExercise.releaseDate") whose in-place writes come from the child timeline component and cannot be intercepted to commit a signal rebuild
    newProgrammingExercise: ProgrammingExercise = new ProgrammingExercise(undefined, undefined);

    readonly isSaving = signal(false);
    savedExercises = 0;
    readonly failedExercises = signal<string[]>([]);
    readonly failureOccurred = signal(false);
    private translationBasePath = 'artemisApp.programmingExercise.';
    notificationText?: string;

    // Icons
    faSave = faSave;

    constructor() {
        // Reset on each open, so a reopen for a different selection starts without a stale draft or previous result.
        effect(() => {
            if (this.visible()) {
                untracked(() => {
                    this.notificationText = undefined;
                    this.newProgrammingExercise = new ProgrammingExercise(undefined, undefined);
                    this.savedExercises = 0;
                    this.failedExercises.set([]);
                    this.failureOccurred.set(false);
                    this.isSaving.set(false);
                });
            }
        });
    }

    saveAll() {
        // If no release date is set, we warn the user.
        if (!this.newProgrammingExercise.releaseDate) {
            const confirmNoReleaseDate = this.translateService.instant(
                this.translationBasePath + (this.newProgrammingExercise.startDate ? 'noReleaseDateWarning' : 'noReleaseAndStartDateWarning'),
            );
            if (!window.confirm(confirmNoReleaseDate)) {
                return;
            }
        }
        this.isSaving.set(true);

        this.selectedProgrammingExercises().forEach((programmingExercise) => {
            programmingExercise = this.setNewValues(programmingExercise);
            const requestOptions: { notificationText?: string } = {};
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(programmingExercise.title, this.programmingExerciseService.updateTimeline(programmingExercise, requestOptions));
        });
    }

    /**
     * Replace the programming exercise values with the new given ones
     * @param programmingExercise to update
     * @return the programming exercise with updated values
     */
    setNewValues(programmingExercise: ProgrammingExercise) {
        programmingExercise.releaseDate = this.newProgrammingExercise.releaseDate;
        programmingExercise.startDate = this.newProgrammingExercise.startDate;
        programmingExercise.dueDate = this.newProgrammingExercise.dueDate;
        programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = this.newProgrammingExercise.buildAndTestStudentSubmissionsAfterDueDate;
        programmingExercise.assessmentType = this.newProgrammingExercise.assessmentType;
        programmingExercise.assessmentDueDate = this.newProgrammingExercise.assessmentDueDate;
        programmingExercise.exampleSolutionPublicationDate = this.newProgrammingExercise.exampleSolutionPublicationDate;
        return programmingExercise;
    }

    private subscribeToSaveResponse(exerciseTitle: string | undefined, result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe({
            next: () => this.onSaveSuccess(),
            error: (res: HttpErrorResponse) => this.onSaveError(res, exerciseTitle),
        });
    }

    private onSaveSuccess() {
        this.savedExercises++;
        if (this.savedExercises === this.selectedProgrammingExercises().length) {
            this.isSaving.set(false);
            if (!this.failureOccurred()) {
                this.saved.emit();
                this.visible.set(false);
            }
        }
    }

    private onSaveError(error: HttpErrorResponse, exerciseTitle?: string | undefined) {
        exerciseTitle = exerciseTitle ?? 'undefined exercise';
        this.failureOccurred.set(true);
        this.failedExercises.set([...this.failedExercises(), exerciseTitle]);
        this.savedExercises++;
        if (this.savedExercises === this.selectedProgrammingExercises().length) {
            this.isSaving.set(false);
        }
        window.scrollTo(0, 0);
    }

    closeModal() {
        this.visible.set(false);
    }

    /** Closes the modal (× / cancel). */
    clear() {
        this.visible.set(false);
    }
}
