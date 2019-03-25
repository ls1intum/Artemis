import './vendor.ts';

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { NgbDatepickerConfig } from '@ng-bootstrap/ng-bootstrap';
import { NgxWebstorageModule } from 'ngx-webstorage';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgJhipsterModule } from 'ng-jhipster';
import { Angulartics2Module } from 'angulartics2';

import { AuthInterceptor } from './blocks/interceptor/auth.interceptor';
import { AuthExpiredInterceptor } from './blocks/interceptor/auth-expired.interceptor';
import { ErrorHandlerInterceptor } from './blocks/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from './blocks/interceptor/notification.interceptor';
import { JhiWebsocketService, UserRouteAccessService } from './core';
import { ArTEMiSSharedModule } from './shared';
import { ArTEMiSCoreModule } from 'app/core';
import { ArTEMiSAppRoutingModule } from './app-routing.module';
import { ArTEMiSHomeModule } from './home';
import { ArTEMiSLegalModule } from './legal';
import { ArTEMiSOverviewModule } from './overview';
import { ArTEMiSAccountModule } from './account/account.module';
import { ArTEMiSCourseListModule } from './course-list';
import { ArTEMiSEntityModule } from './entities/entity.module';
import { ArTEMiSInstructorCourseDashboardModule, ArTEMiSInstructorDashboardModule } from './dashboard';
import { ArTEMiSAssessmentDashboardModule } from './modeling-assessment';
import { PaginationConfig } from './blocks/config/uib-pagination.config';
import { DifferencePipe, MomentModule } from 'angular2-moment';
import { ArTEMiSCodeEditorModule } from './code-editor';
import { RepositoryInterceptor, RepositoryService } from './entities/repository';
import { ArTEMiSQuizModule } from './quiz/participate';
import { ArTEMiSTextModule } from './text-editor';
import { ArTEMiSTextAssessmentModule } from './text-assessment';
import { ArTEMiSModelingStatisticsModule } from './modeling-statistics/';
import * as moment from 'moment';
// jhipster-needle-angular-add-module-import JHipster will add new module here
import { ActiveMenuDirective, ErrorComponent, FooterComponent, JhiMainComponent, NavbarComponent, PageRibbonComponent, ProfileService } from './layouts';
import { ArTEMiSApollonDiagramsModule } from './apollon-diagrams';
import { ArTEMiSStatisticModule } from './quiz-statistics/quiz-statistic.module';
import { ArTEMiSModelingEditorModule } from './modeling-editor/modeling-editor.module';
import { QuizExerciseExportComponent } from './entities/quiz-exercise/quiz-exercise-export.component';
import { PendingChangesGuard } from './shared/guard/pending-changes.guard';
import { ArTEMiSInstructorCourseStatsDashboardModule } from 'app/instructor-course-dashboard';
import { ArTEMiSInstructorExerciseStatsDashboardModule } from 'app/instructor-exercise-dashboard';
import { ParticipationDataProvider } from './course-list/exercise-list/participation-data-provider';
import { ArTEMiSTutorCourseDashboardModule } from 'app/tutor-course-dashboard';
import { ArTEMiSTutorExerciseDashboardModule } from 'app/tutor-exercise-dashboard';
import { ArTEMiSExampleSubmissionModule } from 'app/example-text-submission';
import { ArTEMiSComplaintsModule } from 'app/complaints';

@NgModule({
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        ArTEMiSAppRoutingModule,
        NgxWebstorageModule.forRoot({ prefix: 'jhi', separator: '-' }),
        /**
         * @external Moment is a date library for parsing, validating, manipulating, and formatting dates.
         */
        MomentModule,
        NgJhipsterModule.forRoot({
            // set below to true to make alerts look like toast
            alertAsToast: false,
            alertTimeout: 8000,
            i18nEnabled: true,
            defaultI18nLang: 'en'
        }),
        /**
         * @external Angulartics offers Vendor-agnostic analytics and integration with Matomo
         */
        Angulartics2Module.forRoot(),
        ArTEMiSSharedModule.forRoot(),
        ArTEMiSCoreModule,
        ArTEMiSHomeModule,
        ArTEMiSLegalModule,
        ArTEMiSOverviewModule,
        ArTEMiSAccountModule,
        ArTEMiSEntityModule,
        ArTEMiSApollonDiagramsModule,
        ArTEMiSCourseListModule,
        ArTEMiSCodeEditorModule,
        ArTEMiSQuizModule,
        ArTEMiSInstructorCourseDashboardModule,
        ArTEMiSInstructorDashboardModule,
        ArTEMiSAssessmentDashboardModule,
        ArTEMiSStatisticModule,
        ArTEMiSModelingEditorModule,
        ArTEMiSModelingStatisticsModule,
        ArTEMiSTextModule,
        ArTEMiSTextAssessmentModule,
        ArTEMiSInstructorCourseStatsDashboardModule,
        ArTEMiSInstructorExerciseStatsDashboardModule,
        ArTEMiSTutorCourseDashboardModule,
        ArTEMiSTutorExerciseDashboardModule,
        ArTEMiSExampleSubmissionModule,
        ArTEMiSComplaintsModule
        // jhipster-needle-angular-add-module JHipster will add new module here
    ],
    declarations: [
        JhiMainComponent,
        NavbarComponent,
        ErrorComponent,
        PageRibbonComponent,
        ActiveMenuDirective,
        FooterComponent,
        QuizExerciseExportComponent
    ],
    providers: [
        ProfileService,
        RepositoryService,
        PaginationConfig,
        UserRouteAccessService,
        DifferencePipe,
        JhiWebsocketService,
        ParticipationDataProvider,
        PendingChangesGuard,
        /**
         * @description Interceptor declarations:
         * Interceptors are located at 'blocks/interceptor/.
         * All of them implement the HttpInterceptor interface.
         * They can be used to modify API calls or trigger additional function calls.
         * Most interceptors will transform the outgoing request before passing it to
         * the next interceptor in the chain, by calling next.handle(transformedReq).
         * Documentation: https://angular.io/api/common/http/HttpInterceptor
         */
        {
            provide: HTTP_INTERCEPTORS,
            useClass: AuthInterceptor,
            multi: true
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: AuthExpiredInterceptor,
            multi: true
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: ErrorHandlerInterceptor,
            multi: true
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: NotificationInterceptor,
            multi: true
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: RepositoryInterceptor,
            multi: true
        }
    ],
    bootstrap: [JhiMainComponent]
})
export class ArTeMiSAppModule {
    constructor(private dpConfig: NgbDatepickerConfig) {
        this.dpConfig.minDate = { year: moment().year() - 100, month: 1, day: 1 };
    }
}
