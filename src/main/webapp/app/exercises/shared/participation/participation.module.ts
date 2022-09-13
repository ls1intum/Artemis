import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisParticipationSubmissionModule } from 'app/exercises/shared/participation-submission/participation-submission.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisParticipationRoutingModule } from 'app/exercises/shared/participation/participation-routing.module';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ExerciseModeSwitchModule } from 'app/shared/exercise-mode-switch/exercise-mode-switch.module';

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
        ExerciseModeSwitchModule,
    ],
    declarations: [ParticipationComponent],
})
export class ArtemisParticipationModule {}
