import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { TeamUpdateDialogComponent } from 'app/exercise/team/team-update-dialog/team-update-dialog.component';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faPencilAlt, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-team-update-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize()"
            [icon]="team() ? faPencilAlt : faPlus"
            [title]="team() ? 'artemisApp.team.updateTeam.label' : 'artemisApp.team.createTeam.label'"
            (onClick)="openTeamCreateDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class TeamUpdateButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    readonly team = input<Team | undefined>(undefined);
    readonly exercise = input.required<Exercise>();
    readonly buttonSize = input<ButtonSize>(ButtonSize.SMALL);

    readonly save = output<Team>();

    // Icons
    faPencilAlt = faPencilAlt;
    faPlus = faPlus;

    /**
     * Open the dialog for team creation
     * @param {MouseEvent} event - Occurred Mouse Event
     */
    openTeamCreateDialog(event: MouseEvent) {
        event.stopPropagation();
        const team = this.team();
        const exercise = this.exercise();
        const titleLabel = this.translateService.instant(team ? 'artemisApp.team.updateTeam.label' : 'artemisApp.team.createTeam.label');
        const exerciseTitle = exercise.title;
        const header = exerciseTitle ? `${titleLabel} (${exerciseTitle})` : titleLabel;
        const ref = this.dialogService.open(TeamUpdateDialogComponent, {
            header,
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: { team: team || new Team(), exercise },
        });
        ref?.onClose.subscribe((result: Team | undefined) => {
            if (result) {
                this.save.emit(result);
            }
        });
    }
}
