import { Component, computed, inject, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFloppyDisk } from '@fortawesome/free-solid-svg-icons';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { facSaveSuccess } from 'app/foundation/icons/icons';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';

@Component({
    selector: 'jhi-exercise-save-button',
    templateUrl: './exercise-save-button.component.html',
    styleUrls: ['./exercise-save-button.component.scss'],
    imports: [FaIconComponent, TranslateDirective],
})
export class ExerciseSaveButtonComponent {
    protected readonly faFloppyDisk = faFloppyDisk;
    protected readonly facSaveSuccess = facSaveSuccess;

    private readonly examParticipationService = inject(ExamParticipationService);

    submission = input<Submission>();
    save = output<void>();

    // `submission.isSynced` is mutated in place (on an answer/text/model change and on save success/failure), which is
    // invisible to signal bindings under zoneless change detection. Depending on the service's sync-state version forces
    // this to re-evaluate whenever the flag changes, so the button enables on an edit and disables again once synced.
    protected readonly isSynced = computed(() => {
        this.examParticipationService.submissionSyncVersion();
        return this.submission()?.isSynced;
    });

    onSave() {
        this.save.emit();
    }
}
