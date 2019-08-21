import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

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
import { SortByModule } from 'app/components/pipes';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';

const ENTITY_STATES = [...participationRoute, ...participationPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArtemisProgrammingExerciseModule],
    declarations: [
        ParticipationComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
        ParticipationCleanupBuildPlanDialogComponent,
        ParticipationCleanupBuildPlanPopupComponent,
    ],
    entryComponents: [
        ParticipationComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
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
