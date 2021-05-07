import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ArtemisTestModule } from '../../../test.module';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService, TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { ExerciseScoresComponent } from 'app/exercises/shared/exercise-scores/exercise-scores.component';
import { AccountService } from 'app/core/auth/account.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import * as sinon from 'sinon';
import { of } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { BuildPlanButtonDirective } from 'app/exercises/programming/shared/utils/build-plan-button.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { MockTranslateValuesDirective } from '../../course/course-scores/course-scores.component.spec';
import { DifferencePipe } from 'ngx-moment';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

describe('Exercise Scores Component', () => {
    let component: ExerciseScoresComponent;
    let fixture: ComponentFixture<ExerciseScoresComponent>;
    let exerciseService: ExerciseService;
    let accountService: AccountService;
    let resultService: ResultService;
    let profileService: ProfileService;
    let programmingSubmissionService: ProgrammingSubmissionService;

    const router = new MockRouter();

    const route = ({ data: of({ courseId: 1 }), children: [] } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), TranslateTestingModule, FormsModule, NgxDatatableModule],
            declarations: [
                ExerciseScoresComponent,
                MockComponent(AlertComponent),
                MockComponent(ExerciseScoresExportButtonComponent),
                MockComponent(ProgrammingAssessmentRepoExportButtonComponent),
                MockComponent(SubmissionExportButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(ResultComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(BuildPlanButtonDirective),
                MockDirective(FeatureToggleLinkDirective),
                MockDirective(MockTranslateValuesDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: Router, useValue: router },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                DifferencePipe,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseScoresComponent);
        component = fixture.componentInstance;
        exerciseService = TestBed.inject(ExerciseService);
        accountService = TestBed.inject(AccountService);
        resultService = TestBed.inject(ResultService);
        profileService = TestBed.inject(ProfileService);
        programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
    });

    afterEach(() => {
        sinon.restore();
    });

    it('get exercise participation link', () => {
        console.log('test');
    });
});
