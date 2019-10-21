import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    TutorGroupComponent,
    TutorGroupDeleteDialogComponent,
    TutorGroupDeletePopupComponent,
    TutorGroupDetailComponent,
    tutorGroupPopupRoute,
    tutorGroupRoute,
    TutorGroupUpdateComponent,
} from './';

const ENTITY_STATES = [...tutorGroupRoute, ...tutorGroupPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [TutorGroupComponent, TutorGroupDetailComponent, TutorGroupUpdateComponent, TutorGroupDeleteDialogComponent, TutorGroupDeletePopupComponent],
    entryComponents: [TutorGroupComponent, TutorGroupUpdateComponent, TutorGroupDeleteDialogComponent, TutorGroupDeletePopupComponent],
})
export class ArtemisTutorGroupModule {}
