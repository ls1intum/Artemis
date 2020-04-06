import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { AngularStickyThingsModule } from '@w11k/angular-sticky-things';
import { TeamsComponent } from 'app/exercises/shared/team/teams.component';
import { teamRoute } from 'app/exercises/shared/team/team.route';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { TeamUpdateDialogComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-dialog.component';
import { TeamStudentSearchComponent } from 'app/exercises/shared/team/team-student-search/team-student-search.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-students-list/team-students-list.component';
import { TeamStudentsOnlineListComponent } from 'app/exercises/shared/team/team-students-online-list/team-students-online-list.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { TeamOwnerSearchComponent } from 'app/exercises/shared/team/team-owner-search/team-owner-search.component';
import { TeamParticipateInfoBoxComponent } from 'app/exercises/shared/team/team-participate-info-box/team-participate-info-box.component';

const ENTITY_STATES = [...teamRoute];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, NgxDatatableModule, AngularStickyThingsModule, ArtemisDataTableModule, ArtemisSharedComponentModule],
    declarations: [
        TeamsComponent,
        TeamUpdateButtonComponent,
        TeamUpdateDialogComponent,
        TeamDeleteButtonComponent,
        TeamStudentSearchComponent,
        TeamOwnerSearchComponent,
        TeamStudentsListComponent,
        TeamStudentsOnlineListComponent,
        TeamParticipateInfoBoxComponent,
        TeamComponent,
    ],
    exports: [TeamUpdateButtonComponent, TeamStudentsListComponent, TeamParticipateInfoBoxComponent],
    entryComponents: [TeamUpdateDialogComponent],
})
export class ArtemisTeamModule {}
