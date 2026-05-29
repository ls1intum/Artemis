import { Component, inject, input } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';

@Component({
    selector: 'jhi-external-submission',
    template: `
        <jhi-button
            [btnType]="ButtonType.WARNING"
            [btnSize]="ButtonSize.SMALL"
            [icon]="faPlus"
            [title]="'entity.action.addExternalSubmission'"
            [shouldToggle]="true"
            [toggleBreakpoint]="'md'"
            (onClick)="openExternalSubmissionDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class ExternalSubmissionButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

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
