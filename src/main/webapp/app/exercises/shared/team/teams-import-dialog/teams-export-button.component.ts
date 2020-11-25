import { Component, EventEmitter, Input, Output } from '@angular/core';
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
            [icon]="'plus'"
            [title]="'artemisApp.team.importTeams.buttonLabel'"
            (onClick)="exportTeams($event)"
        ></jhi-button>
    `,
})
export class TeamsExportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() teams: Team[];
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() save: EventEmitter<Team[]> = new EventEmitter();

    constructor(private teamService: TeamService, private jhiAlertService: JhiAlertService) {}

    exportTeams(event: MouseEvent) {
        event.stopPropagation();
        try {
            this.teamService.exportTeams(this.teams);
        } catch (e) {
            this.jhiAlertService.error('artemisApp.team.errors.studentsWithoutRegistrationNumbers', { students: e.message });
        }
    }
}
