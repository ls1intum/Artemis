import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

import { ArtemisSharedModule } from 'app/shared';
import {
    ParticipationCleanupBuildPlanDialogComponent,
    ParticipationCleanupBuildPlanPopupComponent,
    ParticipationComponent,
    ParticipationDeleteDialogComponent,
    ParticipationDeletePopupComponent,
    participationPopupRoute,
    ParticipationPopupService,
    participationRoute,
    ParticipationService,
    ParticipationWebsocketService,
} from './';
import { ParticipationSubmissionComponent } from 'app/entities/participation-submission/participation-submission.component';
import { SortByModule } from 'app/components/pipes';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisResultModule } from 'app/entities/result';
import {
    ParticipationSubmissionDeleteDialogComponent,
    ParticipationSubmissionDeletePopupComponent,
} from 'app/entities/participation-submission/participation-submission-delete-dialog.component';
import { ArtemisExerciseScoresModule } from 'app/scores';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';

const ENTITY_STATES = [...participationRoute, ...participationPopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArtemisExerciseScoresModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisResultModule,
        NgxDatatableModule,
    ],

    declarations: [
        ParticipationComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
        ParticipationSubmissionDeleteDialogComponent,
        ParticipationSubmissionDeletePopupComponent,
        ParticipationCleanupBuildPlanDialogComponent,
        ParticipationCleanupBuildPlanPopupComponent,
        ParticipationSubmissionComponent,
    ],
    entryComponents: [
        ParticipationComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
        ParticipationSubmissionDeleteDialogComponent,
        ParticipationSubmissionDeletePopupComponent,
        ParticipationCleanupBuildPlanDialogComponent,
        ParticipationCleanupBuildPlanPopupComponent,
    ],
    providers: [ParticipationService, ParticipationWebsocketService, ParticipationPopupService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisParticipationModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
