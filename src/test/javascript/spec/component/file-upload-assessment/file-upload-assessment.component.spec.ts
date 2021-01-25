import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { Router } from '@angular/router';
import { FileUploadAssessmentComponent } from 'app/exercises/file-upload/assess/file-upload-assessment.component';
import { DebugElement } from '@angular/core';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { TranslateModule } from '@ngx-translate/core';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { setLatestSubmissionResult, SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Result } from 'app/entities/result.model';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { routes } from 'app/exercises/file-upload/assess/file-upload-assessment.route';
import { By } from '@angular/platform-browser';
import { throwError } from 'rxjs';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';

chai.use(sinonChai);

const expect = chai.expect;

describe('FileUploadAssessmentComponent', () => {
    let comp: FileUploadAssessmentComponent;
    let fixture: ComponentFixture<FileUploadAssessmentComponent>;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let getFileUploadSubmissionForExerciseWithoutAssessmentStub: SinonStub;
    let debugElement: DebugElement;
    let router: Router;
    const activatedRouteMock: MockActivatedRoute = new MockActivatedRoute();

    const exercise = { id: 20, type: ExerciseType.FILE_UPLOAD } as FileUploadExercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                RouterTestingModule.withRoutes([routes[0]]),
                ArtemisAssessmentSharedModule,
                ModelingAssessmentModule,
                TranslateModule.forRoot(),
            ],
            declarations: [
                FileUploadAssessmentComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(AssessmentInstructionsComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
            ],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadAssessmentComponent);
                comp = fixture.componentInstance;
                comp.exercise = exercise;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
                getFileUploadSubmissionForExerciseWithoutAssessmentStub = stub(
                    fileUploadSubmissionService,
                    'getFileUploadSubmissionForExerciseForCorrectionRoundWithoutAssessment',
                );

                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });
            });
    });

    afterEach(() => {
        getFileUploadSubmissionForExerciseWithoutAssessmentStub.restore();
    });

    it('AssessNextButton should be visible', fakeAsync(() => {
        activatedRouteMock.testParams = { exerciseId: 1, submissionId: 'new' };
        getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ status: 404 }));

        // set all attributes for comp
        comp.ngOnInit();
        tick();

        comp.userId = 99;
        comp.submission = {
            submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD,
            id: 2278,
            submitted: true,
            type: SubmissionType.MANUAL,
            submissionDate: moment('2019-07-09T10:47:33.244Z'),
            participation: ({ type: ParticipationType.STUDENT, exercise } as unknown) as Participation,
        } as FileUploadSubmission;
        comp.result = new Result();
        comp.result.id = 2374;
        comp.result.resultString = '1 of 12 points';
        comp.result.completionDate = moment('2019-07-09T11:51:23.251Z');
        comp.result.successful = false;
        comp.result.score = 1;
        comp.result.rated = true;
        comp.result.hasFeedback = false;
        comp.result.submission = comp.submission;
        comp.result.participation = undefined;
        comp.result.assessmentType = AssessmentType.MANUAL;
        comp.result.exampleResult = false;
        comp.result.hasComplaint = false;
        setLatestSubmissionResult(comp.submission, comp.result);
        comp.submission.participation!.submissions = [comp.submission];
        comp.submission.participation!.results = [comp.submission.latestResult!];
        comp.isAssessor = true;
        comp.isAtLeastInstructor = true;
        comp.assessmentsAreValid = true;
        comp.isLoading = false;

        fixture.detectChanges();

        const assessNextButton = debugElement.query(By.css('#assessNextButton'));
        expect(assessNextButton).to.exist;
    }));
});
