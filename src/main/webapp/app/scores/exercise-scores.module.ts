import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UserRouteAccessService } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import { MomentModule } from 'ngx-moment';
import { ExerciseScoresComponent } from './exercise-scores.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisResultModule } from 'app/entities/result';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import {
    ExerciseScoresPopupService,
    ExerciseScoresRepoExportComponent,
    ExerciseScoresRepoExportPopupComponent,
    ExerciseScoresResultDialogComponent,
    ExerciseScoresResultResultPopupComponent,
} from 'app/scores';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/exercise/:exerciseId/dashboard',
        component: ExerciseScoresComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'participation/:participationId/result/new',
        component: ExerciseScoresResultResultPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'exercise/:id/exportRepos',
        component: ExerciseScoresRepoExportPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, RouterModule.forChild(ENTITY_STATES), NgbModule, ArtemisResultModule, SortByModule, FormDateTimePickerModule],
    declarations: [
        ExerciseScoresComponent,
        ExerciseScoresResultDialogComponent,
        ExerciseScoresResultResultPopupComponent,
        ExerciseScoresRepoExportComponent,
        ExerciseScoresRepoExportPopupComponent,
    ],
    entryComponents: [
        ExerciseScoresComponent,
        ExerciseScoresResultDialogComponent,
        ExerciseScoresResultResultPopupComponent,
        ExerciseScoresRepoExportComponent,
        ExerciseScoresRepoExportPopupComponent,
    ],
    providers: [ExerciseScoresPopupService],
})
export class ArtemisExerciseScoresModule {}
