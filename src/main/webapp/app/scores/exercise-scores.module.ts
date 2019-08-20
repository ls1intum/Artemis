import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper, UserRouteAccessService } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import { HomeComponent } from 'app/home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from 'app/layouts';
import { ExerciseScoresComponent } from './exercise-scores.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisResultModule, ResultComponent, ResultDetailComponent } from 'app/entities/result';
import { InstructorDashboardArchivePopupComponent, ProgrammingExerciseArchiveDialogComponent, } from 'app/entities/programming-exercise/programming-exercise-archive-dialog.component';
import { InstructorDashboardCleanupPopupComponent, ProgrammingExerciseCleanupDialogComponent, } from 'app/entities/programming-exercise/programming-exercise-cleanup-dialog.component';
import { ExerciseScoresRepoExportComponent, InstructorDashboardExportReposPopupComponent } from './exercise-scores-repo-export-dialog.component';
import { ExerciseScoresPopupService } from './exercise-scores-popup.service';
import { ExerciseScoresResultDialogComponent, InstructorDashboardResultPopupComponent } from './exercise-scores-result-dialog.component';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

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
        component: InstructorDashboardResultPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'exercise/:id/archive',
        component: InstructorDashboardArchivePopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'exercise/:id/cleanup',
        component: InstructorDashboardCleanupPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'exercise/:id/exportRepos',
        component: InstructorDashboardExportReposPopupComponent,
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
        ProgrammingExerciseArchiveDialogComponent,
        InstructorDashboardArchivePopupComponent,
        ProgrammingExerciseCleanupDialogComponent,
        InstructorDashboardCleanupPopupComponent,
        ExerciseScoresResultDialogComponent,
        InstructorDashboardResultPopupComponent,
        ExerciseScoresRepoExportComponent,
        InstructorDashboardExportReposPopupComponent,
    ],
    entryComponents: [
        HomeComponent,
        ExerciseScoresComponent,
        JhiMainComponent,
        ResultComponent,
        ResultDetailComponent,
        ProgrammingExerciseArchiveDialogComponent,
        InstructorDashboardArchivePopupComponent,
        ProgrammingExerciseCleanupDialogComponent,
        InstructorDashboardCleanupPopupComponent,
        ExerciseScoresResultDialogComponent,
        InstructorDashboardResultPopupComponent,
        ExerciseScoresRepoExportComponent,
        InstructorDashboardExportReposPopupComponent,
    ],
    providers: [ExerciseScoresPopupService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisExerciseScoresModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
