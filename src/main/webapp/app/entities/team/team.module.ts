import { NgModule, ModuleWithProviders } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TeamsComponent } from 'app/entities/team/teams.component';
import { teamRoute } from 'app/entities/team/team.route';
import { ArtemisDataTableModule } from 'app/components/data-table/data-table.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TeamUpdateButtonComponent } from 'app/entities/team/team-update-dialog/team-update-button.component';
import { TeamUpdateDialogComponent } from 'app/entities/team/team-update-dialog/team-update-dialog.component';
import { TeamStudentSearchComponent } from 'app/entities/team/team-student-search/team-student-search.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...teamRoute];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, NgxDatatableModule, ArtemisDataTableModule, ArtemisSharedComponentModule],
    declarations: [TeamsComponent, TeamUpdateButtonComponent, TeamUpdateDialogComponent, TeamStudentSearchComponent],
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
