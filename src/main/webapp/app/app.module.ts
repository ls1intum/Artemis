import './vendor.ts';

import { Injector, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { UpgradeModule } from '@angular/upgrade/static';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { LocalStorageService, Ng2Webstorage, SessionStorageService } from 'ngx-webstorage';
import { JhiEventManager } from 'ng-jhipster';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { AuthInterceptor } from './blocks/interceptor/auth.interceptor';
import { AuthExpiredInterceptor } from './blocks/interceptor/auth-expired.interceptor';
import { ErrorHandlerInterceptor } from './blocks/interceptor/errorhandler.interceptor';
import { NotificationInterceptor } from './blocks/interceptor/notification.interceptor';
import { ArTEMiSSharedModule, JhiWebsocketService, UserRouteAccessService } from './shared';
import { ArTEMiSAppRoutingModule } from './app-routing.module';
import { ArTEMiSHomeModule } from './home/home.module';
import { ArTEMiSAdminModule } from './admin/admin.module';
import { ArTEMiSAccountModule } from './account/account.module';
import { ArTEMiSCoursesModule } from './courses';
import { ArTEMiSEntityModule } from './entities/entity.module';
import { ArTEMiSInstructorCourseDashboardModule, ArTEMiSInstructorDashboardModule } from './instructor-dashboard';
import { PaginationConfig } from './blocks/config/uib-pagination.config';
import { DifferencePipe, MomentModule } from 'angular2-moment';
import { EditorComponentWrapper } from './editor/editor.directive';
import { EditorComponent } from './editor';
import { ArTEMiSEditorModule } from './editor';
import { RepositoryInterceptor, RepositoryService } from './entities/repository';
import { ArTEMiSQuizModule } from './quiz/participate';
import { ng1AuthServiceProvider } from './shared/auth/ng1-auth-wrapper.service';
import { ng1JhiWebsocketService } from './shared/websocket/ng1-websocket.service';
// jhipster-needle-angular-add-module-import JHipster will add new module here
import { ActiveMenuDirective, ErrorComponent, FooterComponent, JhiMainComponent, NavbarComponent, PageRibbonComponent, ProfileService } from './layouts';
import { ArTEMiSApollonDiagramsModule } from './apollon-diagrams';
import { QuizExerciseDetailWrapper } from './entities/quiz-exercise/quiz-exercise-detail.directive';
import { QuizExerciseDetailComponent } from './entities/quiz-exercise';
import { ng1TranslateService } from './shared/language/ng1-translate.service';
import { ng1TranslatePartialLoaderService } from './shared/language/ng1-translate-partial-loader.service';
import { ArTEMiSStatisticModule } from './statistics/statistic.module';
import { ArTEMiSModelingEditorModule } from './modeling-editor/modeling-editor.module';
import { QuizReEvaluateWrapper } from './quiz/re-evaluate/quiz-re-evaluate.directive';
import { QuizReEvaluateComponent } from './quiz/re-evaluate/quiz-re-evaluate.component';
import { Principal } from './shared';

declare var angular: any;

@NgModule({
    imports: [
        BrowserModule,
        /**
         * @description Import UpgradeModule:
         * We need the UpgradeModule provided by Angular to up- and downgrade components and services.
         * Reading: https://angular.io/guide/upgrade
         */
        UpgradeModule,
        ArTEMiSAppRoutingModule,
        Ng2Webstorage.forRoot({ prefix: 'jhi', separator: '-'}),
        /**
         * @external Moment is a date library for parsing, validating, manipulating, and formatting dates.
         */
        MomentModule,
        NgbModule.forRoot(),
        ArTEMiSSharedModule,
        ArTEMiSHomeModule,
        ArTEMiSAdminModule,
        ArTEMiSAccountModule,
        ArTEMiSEntityModule,
        ArTEMiSApollonDiagramsModule,
        ArTEMiSCoursesModule,
        ArTEMiSEditorModule,
        ArTEMiSQuizModule,
        ArTEMiSInstructorCourseDashboardModule,
        ArTEMiSInstructorDashboardModule,
        ArTEMiSStatisticModule,
        ArTEMiSModelingEditorModule
        // jhipster-needle-angular-add-module JHipster will add new module here
    ],
    declarations: [
        JhiMainComponent,
        NavbarComponent,
        ErrorComponent,
        PageRibbonComponent,
        ActiveMenuDirective,
        FooterComponent,
        QuizExerciseDetailWrapper,
        QuizExerciseDetailComponent,
        QuizReEvaluateWrapper,
        QuizReEvaluateComponent
    ],
    /**
     * @description Entry components:
     * entryComponents are loaded imperatively, which means they are bootstrapped directly by the app.
     * Besides our JhiMainComponent, we need to declare each upgraded component in this array as well.
     */
    entryComponents: [
        /** @desc Angular app main component **/
        JhiMainComponent,
        /** @desc Upgraded editor component**/
        EditorComponent,
        /** @desc Upgraded QuizExerciseDetails component **/
        QuizExerciseDetailComponent
    ],
    providers: [
        ProfileService,
        RepositoryService,
        PaginationConfig,
        UserRouteAccessService,
        DifferencePipe,
        JhiWebsocketService,
        Principal,
    /**
     * @description Providing $scope:
     *  Angular 2+ is using this instead of $scope.
     *  Usage of $scope/$rootScope is discouraged for upgraded/hybrid setups in general.
     *  But there are cases where an upgraded service/components needs access to it.
     *  Therefore, we inject it in the app module to make it available.
     **/
    {
        provide: '$scope',
        useExisting: '$rootScope'
    },
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
            multi: true,
            deps: [
                LocalStorageService,
                SessionStorageService
            ]
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: AuthExpiredInterceptor,
            multi: true,
            deps: [
                Injector
            ]
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: ErrorHandlerInterceptor,
            multi: true,
            deps: [
                JhiEventManager
            ]
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: NotificationInterceptor,
            multi: true,
            deps: [
                Injector
            ]
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: RepositoryInterceptor,
            multi: true
        },
        /**
         * @description Include the upgraded ng1 login service:
         * Running a hybrid setup leads to having to manage two separate applications.
         * Here we provide the upgraded login service from the legacy app.
         * This enables us to login users to the legacy app when they provide their credentials in the ng5 app.
         */
        ng1AuthServiceProvider,
        /**
         * @description Include the upgraded ng1 websocket service:
         * Provides an upgraded instance of the ng1 websocket service to our ng5 app.
         * This is required for managing the websocket interactions for both apps.
         */
        ng1JhiWebsocketService,
        ng1TranslateService,
        ng1TranslatePartialLoaderService
    ],
    bootstrap: [ JhiMainComponent ]
})
export class ArTEMiSAppModule {}
