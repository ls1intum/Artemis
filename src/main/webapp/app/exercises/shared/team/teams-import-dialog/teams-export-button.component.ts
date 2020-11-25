import { Component, Input } from '@angular/core';
import { Team } from 'app/entities/team.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { JhiAlertService } from 'ng-jhipster';
import { TeamService } from '../team.service';

@Component({
    selector: 'jhi-teams-export-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="'file-export'"
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

    constructor(private teamService: TeamService, private jhiAlertService: JhiAlertService) {}

    /**
     * Export teams or show students if there is an error
     * @param {MouseEvent} event - To stop propagation
     */
    exportTeams(event: MouseEvent) {
        event.stopPropagation();
        try {
            this.teamService.exportTeams(this.teams);
        } catch (e) {
            this.jhiAlertService.error('artemisApp.team.errors.studentsWithoutRegistrationNumbers', { students: e.message });
        }
    }
}
