import { Component, inject, input } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogService } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-external-submission',
    template: `
        <tumaet-ui-button
            [severity]="outlined() ? 'secondary' : 'warn'"
            [variant]="outlined() ? 'outlined' : 'solid'"
            size="small"
            [icon]="faPlus"
            [ariaLabel]="'entity.action.addExternalSubmission' | artemisTranslate"
            (clicked)="openExternalSubmissionDialog($event)"
        >
            <span class="hidden md:inline" jhiTranslate="entity.action.addExternalSubmission"></span>
        </tumaet-ui-button>
    `,
    imports: [TumAetUiButtonComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class ExternalSubmissionButtonComponent {
    private readonly dialogService = inject(TumAetUiDialogService);
    private readonly translateService = inject(TranslateService);

    readonly exercise = input.required<Exercise>();
    readonly outlined = input(false);

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
            size: 'large',
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            inputValues: { exercise: this.exercise() },
        });
    }
}
