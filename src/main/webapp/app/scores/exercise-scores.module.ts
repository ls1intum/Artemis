import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { UserRouteAccessService } from 'app/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

import { ArtemisSharedModule } from 'app/shared';
import { HomeComponent } from 'app/home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from 'app/layouts';
import { ExerciseScoresComponent } from './exercise-scores.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisResultModule, ResultComponent, ResultDetailComponent } from 'app/entities/result';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import {
    ExerciseScoresPopupService,
    ExerciseScoresRepoExportComponent,
    ExerciseScoresResultDialogComponent,
    ExerciseScoresRepoExportPopupComponent,
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
