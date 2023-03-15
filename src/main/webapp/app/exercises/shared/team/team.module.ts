import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';

import { TeamsImportFromFileFormComponent } from './teams-import-dialog/teams-import-from-file-form.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { TeamExerciseSearchComponent } from 'app/exercises/shared/team/team-exercise-search/team-exercise-search.component';
import { TeamOwnerSearchComponent } from 'app/exercises/shared/team/team-owner-search/team-owner-search.component';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { TeamStudentSearchComponent } from 'app/exercises/shared/team/team-student-search/team-student-search.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { TeamUpdateDialogComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-dialog.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { teamRoute } from 'app/exercises/shared/team/team.route';
import { TeamsExportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-export-button.component';
import { TeamsImportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-button.component';
import { TeamsImportDialogComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-dialog.component';
import { TeamsComponent } from 'app/exercises/shared/team/teams.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
