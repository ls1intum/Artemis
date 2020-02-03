import { NgModule, ModuleWithProviders } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TeamsComponent } from 'app/entities/team/teams.component';
import { teamRoute } from 'app/entities/team/team.route';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisDataTableModule } from 'app/components/data-table/data-table.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TeamCreateButtonComponent } from 'app/entities/team/team-create-dialog/team-create-button.component';
import { TeamCreateDialogComponent } from 'app/entities/team/team-create-dialog/team-create-dialog.component';

const ENTITY_STATES = [...teamRoute];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, NgxDatatableModule, ArtemisDataTableModule, ArtemisSharedComponentModule],
    declarations: [TeamsComponent, TeamCreateButtonComponent, TeamCreateDialogComponent],
    exports: [TeamCreateButtonComponent],
    entryComponents: [TeamsComponent, TeamCreateDialogComponent],
})
export class ArtemisTeamModule {
    static forRoot(): ModuleWithProviders {
        return {
            ngModule: ArtemisTeamModule,
        };
    }
}
