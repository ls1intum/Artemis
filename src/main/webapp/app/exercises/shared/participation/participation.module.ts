import { ModuleWithProviders, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/manage/actions/programming-exercise-actions.module';
import { ArtemisParticipationSubmissionModule } from 'app/exercises/shared/participation-submission/participation-submission.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { participationRoute } from 'app/exercises/shared/participation/participation.route';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/manage/utils/programming-exercise-utils.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';

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
