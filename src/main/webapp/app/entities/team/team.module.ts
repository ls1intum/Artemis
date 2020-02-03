import { NgModule, ModuleWithProviders } from '@angular/core';
import { RouterModule } from '@angular/router';

import { TeamsComponent } from 'app/entities/team/teams.component';
import { teamRoute } from 'app/entities/team/team.route';

const ENTITY_STATES = [...teamRoute];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES)],
    declarations: [TeamsComponent],
    entryComponents: [TeamsComponent],
})
export class ArtemisTeamModule {
    static forRoot(): ModuleWithProviders {
        return {
            ngModule: ArtemisTeamModule,
        };
    }
}
