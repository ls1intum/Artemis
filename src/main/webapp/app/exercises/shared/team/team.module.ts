import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TeamsComponent } from 'app/exercises/shared/team/teams.component';
import { teamRoute } from 'app/exercises/shared/team/team.route';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { TeamUpdateDialogComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-dialog.component';
import { TeamStudentSearchComponent } from 'app/exercises/shared/team/team-student-search/team-student-search.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-students-list/team-students-list.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';

const ENTITY_STATES = [...teamRoute];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, NgxDatatableModule, ArtemisDataTableModule, ArtemisSharedComponentModule],
    declarations: [
        TeamsComponent,
        TeamUpdateButtonComponent,
        TeamUpdateDialogComponent,
        TeamDeleteButtonComponent,
        TeamStudentSearchComponent,
        TeamStudentsListComponent,
        TeamComponent,
    ],
    exports: [TeamUpdateButtonComponent, TeamStudentsListComponent],
    entryComponents: [TeamUpdateDialogComponent],
})
export class ArtemisTeamModule {}
