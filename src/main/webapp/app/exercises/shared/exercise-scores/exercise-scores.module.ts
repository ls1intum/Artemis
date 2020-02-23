import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ExerciseScoresComponent } from './exercise-scores.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment/programming-assessment.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/manage/utils/programming-exercise-utils.module';
import { SortByPipe } from 'app/shared/pipes/sort-by.pipe';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

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
        ProgrammingExerciseUtilsModule,
    ],
    declarations: [ExerciseScoresComponent],
    entryComponents: [ExerciseScoresComponent],
    providers: [SortByPipe],
})
export class ArtemisExerciseScoresModule {}
