import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { TeamsImportDialogComponent } from 'app/exercise/team/teams-import-dialog/teams-import-dialog.component';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-teams-import-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize()"
            [icon]="faPlus"
            [title]="'artemisApp.team.importTeams.buttonLabel'"
            (onClick)="openTeamsImportDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class TeamsImportButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    readonly exercise = input.required<Exercise>();
    readonly teams = input.required<Team[]>();
    readonly buttonSize = input<ButtonSize>(ButtonSize.SMALL);

    readonly save = output<Team[]>();

    // Icons
    faPlus = faPlus;

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
