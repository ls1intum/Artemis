import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming/manage/assess/repo-export/export-dialog/programming-assessment-repo-export-dialog.component';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-programming-assessment-repo-export',
    template: `
        <button
            type="button"
            tumAetUiButton
            size="small"
            severity="primary"
            [jhiFeatureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
            [overwriteDisabled]="!programmingExercises() || false"
            [attr.aria-label]="
                singleParticipantMode() ? 'artemisApp.instructorDashboard.exportRepos.titleSingle' : ('artemisApp.instructorDashboard.exportRepos.title' | artemisTranslate)
            "
            (click)="openRepoExportDialog($event)"
        >
            <fa-icon [icon]="faDownload" />
            <span [jhiTranslate]="singleParticipantMode() ? 'artemisApp.instructorDashboard.exportRepos.titleSingle' : 'artemisApp.instructorDashboard.exportRepos.title'"></span>
        </button>
    `,
    imports: [TumAetUiButtonDirective, FaIconComponent, ArtemisTranslatePipe, FeatureToggleDirective, TranslateDirective],
})
export class ProgrammingAssessmentRepoExportButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

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
        // button's destruction: on the participation page the button lives inside an export popover that
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
