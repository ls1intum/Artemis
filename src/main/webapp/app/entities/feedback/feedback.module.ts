import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    FeedbackComponent,
    FeedbackDetailComponent,
    FeedbackUpdateComponent,
    FeedbackDeletePopupComponent,
    FeedbackDeleteDialogComponent,
    feedbackRoute,
    feedbackPopupRoute
} from './';

const ENTITY_STATES = [...feedbackRoute, ...feedbackPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        FeedbackComponent,
        FeedbackDetailComponent,
        FeedbackUpdateComponent,
        FeedbackDeleteDialogComponent,
        FeedbackDeletePopupComponent
    ],
    entryComponents: [FeedbackComponent, FeedbackUpdateComponent, FeedbackDeleteDialogComponent, FeedbackDeletePopupComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSFeedbackModule {}
