import { NgModule, ModuleWithProviders } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TeamsComponent } from 'app/entities/team/teams.component';
import { teamRoute } from 'app/entities/team/team.route';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisDataTableModule } from 'app/components/data-table/data-table.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TeamUpdateButtonComponent } from 'app/entities/team/team-update-dialog/team-update-button.component';
import { TeamUpdateDialogComponent } from 'app/entities/team/team-update-dialog/team-update-dialog.component';
import { StudentSearchComponent } from 'app/entities/team/student-search/student-search.component';

const ENTITY_STATES = [...teamRoute];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, NgxDatatableModule, ArtemisDataTableModule, ArtemisSharedComponentModule],
    declarations: [TeamsComponent, TeamUpdateButtonComponent, TeamUpdateDialogComponent, StudentSearchComponent],
    exports: [TeamUpdateButtonComponent],
    entryComponents: [TeamsComponent, TeamUpdateDialogComponent],
})
export class ArtemisTeamModule {
    static forRoot(): ModuleWithProviders {
        return {
            ngModule: ArtemisTeamModule,
        };
    }
}
