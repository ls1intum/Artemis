import { ModuleWithProviders, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ArtemisParticipationSubmissionModule } from 'app/entities/participation-submission/participation-submission.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisDataTableModule } from 'app/components/data-table/data-table.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ParticipationComponent } from 'app/entities/participation/participation.component';
import { participationRoute } from 'app/entities/participation/participation.route';
import { ProgrammingExerciseUtilsModule } from 'app/entities/programming-exercise/utils/programming-exercise-utils.module';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import { ArtemisExerciseScoresModule } from 'app/scores/exercise-scores.module';

const ENTITY_STATES = [...participationRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArtemisExerciseScoresModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisParticipationSubmissionModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        FeatureToggleModule,
        ProgrammingExerciseUtilsModule,
    ],

    declarations: [ParticipationComponent],
    entryComponents: [ParticipationComponent],
})
export class ArtemisParticipationModule {
    static forRoot(): ModuleWithProviders {
        return {
            ngModule: ArtemisParticipationModule,
        };
    }
}
