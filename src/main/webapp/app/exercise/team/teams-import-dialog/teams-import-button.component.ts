import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamsImportDialogComponent } from 'app/exercise/team/teams-import-dialog/teams-import-dialog.component';
import { ButtonDirective } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-teams-import-button',
    template: `
        <button pButton severity="primary" size="small" (click)="openTeamsImportDialog($event)">
            <i class="pi pi-upload"></i>
            <span>{{ 'artemisApp.team.importTeams.buttonLabel' | artemisTranslate }}</span>
        </button>
    `,
    imports: [ButtonDirective, ArtemisTranslatePipe],
})
export class TeamsImportButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    readonly exercise = input.required<Exercise>();
    readonly teams = input.required<Team[]>();

    readonly save = output<Team[]>();

    /**
     * Open up import dialog for teams
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openTeamsImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const exercise = this.exercise();
        const titleLabel = this.translateService.instant('artemisApp.team.importTeams.buttonLabel');
        const exerciseTitle = exercise.title;
        const header = exerciseTitle ? `${titleLabel} (${exerciseTitle})` : titleLabel;
        const ref = this.dialogService.open(TeamsImportDialogComponent, {
            header,
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: { exercise, teams: this.teams() },
        });
        ref?.onClose.subscribe((teams: Team[] | undefined) => {
            if (teams) {
                this.save.emit(teams);
            }
        });
    }
}
