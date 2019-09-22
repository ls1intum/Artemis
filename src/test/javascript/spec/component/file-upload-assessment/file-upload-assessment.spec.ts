import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService, JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../mocks';
import { UpdatingResultComponent } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared';
import { ExerciseType } from 'app/entities/exercise';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { FileUploadAssessmentComponent } from 'app/file-upload-assessment/file-upload-assessment.component';
import { FileUploadSubmission, FileUploadSubmissionService } from 'app/entities/file-upload-submission';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { fileUploadAssessmentRoutes } from 'app/file-upload-assessment/file-upload-assessment.route';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { FileUploadAssessmentDetailComponent } from 'app/file-upload-assessment/file-upload-assessment-detail/file-upload-assessment-detail.component';

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

    const exercise = { id: 20, type: ExerciseType.FILE_UPLOAD } as FileUploadExercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, RouterTestingModule.withRoutes([fileUploadAssessmentRoutes[0]])],
            declarations: [
                FileUploadAssessmentComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(ResizableInstructionsComponent),
                MockComponent(FileUploadAssessmentDetailComponent),
                MockComponent(ComplaintsForTutorComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
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
                fileUploadSubmissionService = TestBed.get(FileUploadSubmissionService);
                getFileUploadSubmissionForExerciseWithoutAssessmentStub = stub(fileUploadSubmissionService, 'getFileUploadSubmissionForExerciseWithoutAssessment');

                router.initialNavigation();
            });
    });

    afterEach(() => {
        getFileUploadSubmissionForExerciseWithoutAssessmentStub.restore();
    });

    it(
        'AssessNextButton should be visible, the method assessNextOptimal should be invoked ' + 'and the url should change after clicking on the button',
        fakeAsync(() => {
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
            } as FileUploadSubmission;
            comp.result = {
                id: 2374,
                resultString: '1 of 12 points',
                completionDate: moment('2019-07-09T11:51:23.251Z'),
                successful: false,
                score: 8,
                rated: true,
                hasFeedback: false,
                submission: comp.submission,
            };
            comp.isAssessor = true;
            comp.isAtLeastInstructor = true;
            comp.assessmentsAreValid = true;
            const unassessedSubmission = { submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD, id: 2279, submitted: true, type: 'MANUAL' };

            fixture.detectChanges();

            // check if assessNextButton is available
            const assessNextButton = debugElement.query(By.css('#assessNextButton'));
            expect(assessNextButton).to.exist;

            // check if getTextSubmissionForExerciseWithoutAssessment() is called and works
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(of(unassessedSubmission));
            assessNextButton.nativeElement.click();
            expect(getFileUploadSubmissionForExerciseWithoutAssessmentStub).to.have.been.called;
            expect(comp.unassessedSubmission).to.be.deep.equal(unassessedSubmission);

            // check if the url changes when you clicked on assessNextAssessmentButton
            tick();
            expect(location.path()).to.be.equal('/file-upload-exercise/' + comp.exercise.id + '/submission/' + comp.unassessedSubmission.id + '/assessment');

            fixture.destroy();
            flush();
        }),
    );
});
