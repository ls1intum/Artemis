import { Component, inject, input } from '@angular/core';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { TeamService } from '../team.service';
import { faFileExport } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-teams-export-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize()"
            [icon]="faFileExport"
            [title]="'artemisApp.team.exportTeams.buttonLabel'"
            (onClick)="exportTeams($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class TeamsExportButtonComponent {
    private teamService = inject(TeamService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    readonly teams = input.required<Team[]>();
    readonly buttonSize = input<ButtonSize>(ButtonSize.SMALL);

    // Icons
    faFileExport = faFileExport;

    /**
     * Export teams or show students if there is an error
     * @param {MouseEvent} event - To stop propagation
     */
    exportTeams(event: MouseEvent) {
        event.stopPropagation();
        this.teamService.exportTeams(this.teams());
    }
}
