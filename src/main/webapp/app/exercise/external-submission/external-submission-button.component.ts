import { Component, inject, input } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';

@Component({
    selector: 'jhi-external-submission',
    template: `
        <button
            type="button"
            tumAetUiButton
            size="small"
            severity="secondary"
            variant="outlined"
            [attr.aria-label]="'entity.action.addExternalSubmission' | artemisTranslate"
            (click)="openExternalSubmissionDialog($event)"
        >
            <fa-icon [icon]="faPlus" />
            <span class="hidden md:inline" [jhiTranslate]="'entity.action.addExternalSubmission'"></span>
        </button>
    `,
    imports: [TumAetUiButtonDirective, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class ExternalSubmissionButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    readonly exercise = input.required<Exercise>();

    // Icons
    faPlus = faPlus;

    /**
     * Opens modal window for external exercise submission.
     * @param { MouseEvent } event
     */
    openExternalSubmissionDialog(event: MouseEvent) {
        event.stopPropagation();
        this.dialogService.open(ExternalSubmissionDialogComponent, {
            header: this.translateService.instant('artemisApp.submission.createExternal'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            inputValues: { exercise: this.exercise() },
        });
    }
}
