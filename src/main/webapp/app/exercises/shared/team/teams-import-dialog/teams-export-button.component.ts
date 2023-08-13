import { Component, Input } from '@angular/core';
import { Team } from 'app/entities/team.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { TeamService } from '../team.service';
import { faFileExport } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-teams-export-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="faFileExport"
            [title]="'artemisApp.team.exportTeams.buttonLabel'"
            (onClick)="exportTeams($event)"
        ></jhi-button>
    `,
})
export class TeamsExportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() teams: Team[];
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    // Icons
    faFileExport = faFileExport;

    constructor(private teamService: TeamService, private alertService: AlertService) {}

    /**
     * Export teams or show students if there is an error
     * @param {MouseEvent} event - To stop propagation
     */
    exportTeams(event: MouseEvent) {
        event.stopPropagation();
        this.teamService.exportTeams(this.teams);
    }
}
