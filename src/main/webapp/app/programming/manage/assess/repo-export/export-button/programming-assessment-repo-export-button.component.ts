import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming/manage/assess/repo-export/export-dialog/programming-assessment-repo-export-dialog.component';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-programming-assessment-repo-export',
    template: `
        <jhi-button
            [disabled]="!programmingExercises()"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
            [icon]="faDownload"
            [title]="singleParticipantMode() ? 'artemisApp.instructorDashboard.exportRepos.titleSingle' : 'artemisApp.instructorDashboard.exportRepos.title'"
            (onClick)="openRepoExportDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class ProgrammingAssessmentRepoExportButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    readonly participationIdList = input<number[]>();
    readonly participantIdentifierList = input<string>(); // comma separated
    readonly singleParticipantMode = input(false);
    readonly programmingExercises = input<ProgrammingExercise[]>();

    readonly buttonPressed = output<void>();

    // Icons
    faDownload = faDownload;

    /**
     * Stops the propagation of the mouse event and opens the repo export dialog,
     * passing this instance's values through the dialog config data.
     * @param {MouseEvent} event - Mouse event
     */
    openRepoExportDialog(event: MouseEvent) {
        this.buttonPressed.emit();
        event.stopPropagation();
        // Let the dialog own its lifecycle (it is closable / closeOnEscape). It must NOT be tied to this
        // button's destruction: on the exercise-scores page the button lives inside an export popover that
        // closes on press (buttonPressed -> closeExportPopover), destroying this component right after the
        // dialog opens — closing the ref in ngOnDestroy would make the dialog flash and vanish.
        this.dialogService.open(ProgrammingAssessmentRepoExportDialogComponent, {
            header: this.translateService.instant('entity.exportRepos.title'),
            modal: true,
            closable: true,
            closeOnEscape: true,
            width: '50rem',
            data: {
                programmingExercises: this.programmingExercises(),
                participationIdList: this.participationIdList(),
                participantIdentifierList: this.participantIdentifierList(),
                singleParticipantMode: this.singleParticipantMode(),
            },
        });
    }
}
