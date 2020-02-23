import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { tutorGroupPopupRoute, tutorGroupRoute } from 'app/course/tutor-group/tutor-group.route';
import { TutorGroupDeleteDialogComponent, TutorGroupDeletePopupComponent } from 'app/course/tutor-group/tutor-group-delete-dialog.component';
import { TutorGroupComponent } from 'app/course/tutor-group/tutor-group.component';
import { TutorGroupDetailComponent } from 'app/course/tutor-group/tutor-group-detail.component';
import { TutorGroupUpdateComponent } from 'app/course/tutor-group/tutor-group-update.component';

const ENTITY_STATES = [...tutorGroupRoute, ...tutorGroupPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [TutorGroupComponent, TutorGroupDetailComponent, TutorGroupUpdateComponent, TutorGroupDeleteDialogComponent, TutorGroupDeletePopupComponent],
    entryComponents: [TutorGroupComponent, TutorGroupUpdateComponent, TutorGroupDeleteDialogComponent, TutorGroupDeletePopupComponent],
})
export class ArtemisTutorGroupModule {}
