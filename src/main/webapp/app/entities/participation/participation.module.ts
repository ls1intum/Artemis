import { ModuleWithProviders, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    ParticipationCleanupBuildPlanDialogComponent,
    ParticipationCleanupBuildPlanPopupComponent,
    ParticipationComponent,
    participationPopupRoute,
    ParticipationPopupService,
    participationRoute,
    ParticipationService,
    ParticipationWebsocketService,
} from './';
import { SortByModule } from 'app/components/pipes';
import { ArtemisExerciseScoresModule } from 'app/scores';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ArtemisParticipationSubmissionModule } from 'app/entities/participation-submission/participation-submission.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisDataTableModule } from 'app/components/data-table/data-table.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';

const ENTITY_STATES = [...participationRoute, ...participationPopupRoute];

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
    ],

    declarations: [ParticipationComponent, ParticipationCleanupBuildPlanDialogComponent, ParticipationCleanupBuildPlanPopupComponent],
    entryComponents: [ParticipationComponent, ParticipationCleanupBuildPlanDialogComponent, ParticipationCleanupBuildPlanPopupComponent],
    providers: [ParticipationService, ParticipationPopupService],
})
export class ArtemisParticipationModule {
    static forRoot(): ModuleWithProviders {
        return {
            ngModule: ArtemisParticipationModule,
            providers: [ParticipationWebsocketService],
        };
    }
}
