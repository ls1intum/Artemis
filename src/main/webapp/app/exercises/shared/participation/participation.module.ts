import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisParticipationSubmissionModule } from 'app/exercises/shared/participation-submission/participation-submission.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisParticipationRoutingModule } from 'app/exercises/shared/participation/participation-routing.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';

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
        ArtemisTeamModule,
    ],
    declarations: [ParticipationComponent],
})
export class ArtemisParticipationModule {}
