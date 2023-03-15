import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';

import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisParticipationRoutingModule } from 'app/exercises/shared/participation/participation-routing.module';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { ArtemisParticipationSubmissionModule } from 'app/exercises/shared/participation-submission/participation-submission.module';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisParticipationRoutingModule,
        ArtemisExerciseScoresModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisParticipationSubmissionModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        FeatureToggleModule,
        ArtemisTeamParticipeModule,
        FormDateTimePickerModule,
    ],
    declarations: [ParticipationComponent],
})
export class ArtemisParticipationModule {}
