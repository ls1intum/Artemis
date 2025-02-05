import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisParticipationSubmissionModule } from 'app/exercises/shared/participation-submission/participation-submission.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisParticipationRoutingModule } from 'app/exercises/shared/participation/participation-routing.module';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisParticipationRoutingModule,
        ArtemisExerciseScoresModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisParticipationSubmissionModule,
        NgxDatatableModule,
        ArtemisTeamParticipeModule,
        FormDateTimePickerModule,
        ParticipationComponent,
    ],
})
export class ArtemisParticipationModule {}
