import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { AlertService } from 'app/core/alert/alert.service';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { FileUploadAssessmentComponent } from 'app/exercises/file-upload/assess/file-upload-assessment.component';
import { ResizableInstructionsComponent } from 'app/exercises/text/assess/resizable-instructions/resizable-instructions.component';
import { DebugElement } from '@angular/core';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { TranslateModule } from '@ngx-translate/core';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { UpdatingResultComponent } from 'app/shared/result/updating-result.component';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Result } from 'app/entities/result.model';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { routes } from 'app/exercises/file-upload/assess/file-upload-assessment.route';
import { Course } from 'app/entities/course.model';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadAssessmentComponent', () => {
    let comp: FileUploadAssessmentComponent;
    let fixture: ComponentFixture<FileUploadAssessmentComponent>;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let getFileUploadSubmissionForExerciseWithoutAssessmentStub: SinonStub;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;

    const course = { id: 5 } as Course;
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
                MockComponent(ResizableInstructionsComponent),
                MockComponent(ComplaintsForTutorComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadAssessmentComponent);
                comp = fixture.componentInstance;
                comp.exercise = exercise;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
                getFileUploadSubmissionForExerciseWithoutAssessmentStub = stub(fileUploadSubmissionService, 'getFileUploadSubmissionForExerciseWithoutAssessment');

                router.initialNavigation();
            });
    });

    afterEach(() => {
        getFileUploadSubmissionForExerciseWithoutAssessmentStub.restore();
    });

    it('AssessNextButton should be visible', fakeAsync(() => {
        // set all attributes for comp
        comp.ngOnInit();
        tick();

        comp.userId = 99;
        comp.submission =
            {
                submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD,
                id: 2278,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: moment('2019-07-09T10:47:33.244Z'),
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
        comp.result.participation = null;
        comp.result.assessmentType = AssessmentType.MANUAL;
        comp.result.exampleResult = false;
        comp.result.hasComplaint = false;
        comp.isAssessor = true;
        comp.isAtLeastInstructor = true;
        comp.assessmentsAreValid = true;
        const unassessedSubmission = { submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD, id: 2279, submitted: true, type: 'MANUAL' };

        fixture.detectChanges();
    }));
});
