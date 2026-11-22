import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { Component, inject, input } from '@angular/core';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from '../team.service';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-teams-export-button',
    template: `
        <button tumAetUiButton severity="primary" size="small" (click)="exportTeams($event)">
            <fa-icon [icon]="faDownload" />
            <span>{{ 'artemisApp.team.exportTeams.buttonLabel' | artemisTranslate }}</span>
        </button>
    `,
    imports: [FaIconComponent, TumAetUiButtonDirective, ArtemisTranslatePipe],
})
export class TeamsExportButtonComponent {
    private teamService = inject(TeamService);
    readonly faDownload = faDownload;

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
