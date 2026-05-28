import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TeamUpdateDialogComponent } from 'app/exercise/team/team-update-dialog/team-update-dialog.component';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { faPencilAlt, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

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
    private dialogService = inject(DialogService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    readonly team = input<Team>();
    readonly exercise = input<Exercise>(undefined!);
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
        const dialogRef = this.dialogService.open(TeamUpdateDialogComponent, {
            showHeader: false,
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: { team: this.team() || new Team(), exercise: this.exercise() },
        });

        dialogRef?.onClose.subscribe((team: Team | undefined) => {
            if (team) {
                this.save.emit(team);
            }
        });
    }
}
