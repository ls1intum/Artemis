import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { UserRouteAccessService } from '../core';
import { HomeComponent } from '../home';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { ExerciseDashboardComponent } from './exercise-dashboard.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArTEMiSResultModule, ResultComponent, ResultDetailComponent } from '../entities/result';
import { InstructorDashboardArchivePopupComponent, ProgrammingExerciseArchiveDialogComponent } from '../entities/programming-exercise/programming-exercise-archive-dialog.component';
import { InstructorDashboardCleanupPopupComponent, ProgrammingExerciseCleanupDialogComponent } from '../entities/programming-exercise/programming-exercise-cleanup-dialog.component';
import { InstructorDashboardExportReposComponent, InstructorDashboardExportReposPopupComponent } from './exercise-dashboard-repo-export-dialog.component';
import { ExerciseDashboardPopupService } from './exercise-dashboard-popup.service';
import { ExerciseDashboardResultDialogComponent, InstructorDashboardResultPopupComponent } from './exercise-dashboard-result-dialog.component';
import { SortByModule } from '../components/pipes';
import { FormDateTimePickerModule } from '../shared/dateTimePicker/date-time-picker.module';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/exercise/:exerciseId/dashboard',
        component: ExerciseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'participation/:participationId/result/new',
        component: InstructorDashboardResultPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'exercise/:id/archive',
        component: InstructorDashboardArchivePopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'exercise/:id/cleanup',
        component: InstructorDashboardCleanupPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'exercise/:id/exportRepos',
        component: InstructorDashboardExportReposPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        MomentModule,
        RouterModule.forChild(ENTITY_STATES),
        NgbModule,
        ArTEMiSResultModule,
        SortByModule,
        FormDateTimePickerModule
    ],
    declarations: [
        ExerciseDashboardComponent,
        ProgrammingExerciseArchiveDialogComponent,
        InstructorDashboardArchivePopupComponent,
        ProgrammingExerciseCleanupDialogComponent,
        InstructorDashboardCleanupPopupComponent,
        ExerciseDashboardResultDialogComponent,
        InstructorDashboardResultPopupComponent,
        InstructorDashboardExportReposComponent,
        InstructorDashboardExportReposPopupComponent
    ],
    entryComponents: [
        HomeComponent,
        ExerciseDashboardComponent,
        JhiMainComponent,
        ResultComponent,
        ResultDetailComponent,
        ProgrammingExerciseArchiveDialogComponent,
        InstructorDashboardArchivePopupComponent,
        ProgrammingExerciseCleanupDialogComponent,
        InstructorDashboardCleanupPopupComponent,
        ExerciseDashboardResultDialogComponent,
        InstructorDashboardResultPopupComponent,
        InstructorDashboardExportReposComponent,
        InstructorDashboardExportReposPopupComponent
    ],
    providers: [ExerciseDashboardPopupService]
})
export class ArTEMiSInstructorDashboardModule {}
