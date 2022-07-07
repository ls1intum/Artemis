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
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { TeamOwnerSearchComponent } from 'app/exercises/shared/team/team-owner-search/team-owner-search.component';
import { TeamsImportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-button.component';
import { TeamsImportDialogComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-dialog.component';
import { TeamExerciseSearchComponent } from 'app/exercises/shared/team/team-exercise-search/team-exercise-search.component';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { TeamsImportFromFileFormComponent } from './teams-import-dialog/teams-import-from-file-form.component';
import { TeamsExportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-export-button.component';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';

@NgModule({
    imports: [
        RouterModule.forChild(teamRoute),
        ArtemisSharedModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        ArtemisSharedComponentModule,
        ArtemisResultModule,
        ArtemisTeamParticipeModule,
    ],
    declarations: [
        TeamsComponent,
        TeamUpdateButtonComponent,
        TeamUpdateDialogComponent,
        TeamsImportButtonComponent,
        TeamsExportButtonComponent,
        TeamsImportDialogComponent,
        TeamDeleteButtonComponent,
        TeamStudentSearchComponent,
        TeamOwnerSearchComponent,
        TeamExerciseSearchComponent,
        TeamParticipationTableComponent,
        TeamComponent,
        TeamsImportFromFileFormComponent,
    ],
    exports: [TeamUpdateButtonComponent, TeamsImportButtonComponent, TeamsExportButtonComponent],
})
export class ArtemisTeamModule {}
