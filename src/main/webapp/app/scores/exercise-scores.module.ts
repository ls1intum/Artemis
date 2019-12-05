import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { ArtemisSharedModule } from 'app/shared';
import { MomentModule } from 'ngx-moment';
import { ExerciseScoresComponent } from './exercise-scores.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisResultModule } from 'app/entities/result';
import { SortByModule, SortByPipe } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ExerciseScoresPopupService } from 'app/scores';
import { ArtemisProgrammingAssessmentModule } from 'app/programming-assessment/programming-assessment.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisDataTableModule } from 'app/components/data-table/data-table.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/exercise/:exerciseId/dashboard',
        component: ExerciseScoresComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.exerciseDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [
        ArtemisSharedModule,
        MomentModule,
        RouterModule.forChild(ENTITY_STATES),
        NgbModule,
        ArtemisResultModule,
        SortByModule,
        FormDateTimePickerModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        ArtemisProgrammingAssessmentModule,
        FeatureToggleModule,
    ],
    declarations: [ExerciseScoresComponent],
    entryComponents: [ExerciseScoresComponent],
    providers: [ExerciseScoresPopupService, SortByPipe],
})
export class ArtemisExerciseScoresModule {}
