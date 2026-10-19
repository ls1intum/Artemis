import { Component, inject, input } from '@angular/core';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from '../team.service';
import { ButtonDirective } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-teams-export-button',
    template: `
        <button pButton severity="primary" size="small" (click)="exportTeams($event)">
            <i class="pi pi-download"></i>
            <span>{{ 'artemisApp.team.exportTeams.buttonLabel' | artemisTranslate }}</span>
        </button>
    `,
    imports: [ButtonDirective, ArtemisTranslatePipe],
})
export class TeamsExportButtonComponent {
    private teamService = inject(TeamService);

    readonly teams = input.required<Team[]>();

    /**
     * Export teams or show students if there is an error
     * @param {MouseEvent} event - To stop propagation
     */
    exportTeams(event: MouseEvent) {
        event.stopPropagation();
        this.teamService.exportTeams(this.teams());
    }
}
