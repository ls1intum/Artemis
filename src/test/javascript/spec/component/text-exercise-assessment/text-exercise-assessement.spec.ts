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
import { TextAssessmentComponent } from 'app/exercises/text/assess/text-assessment.component';
import { TextAssessmentEditorComponent } from 'app/exercises/text/assess/text-assessment-editor/text-assessment-editor.component';
import { ResizableInstructionsComponent } from 'app/exercises/text/assess/resizable-instructions/resizable-instructions.component';
import { AssessmentDetailComponent } from 'app/assessment/assessment-detail/assessment-detail.component';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { textAssessmentRoutes } from 'app/exercises/text/assess/text-assessment.route';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { TranslateModule } from '@ngx-translate/core';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { ResultComponent } from 'app/shared/result/result.component';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextAssessmentComponent', () => {
    let comp: TextAssessmentComponent;
    let fixture: ComponentFixture<TextAssessmentComponent>;
    let textSubmissionService: TextSubmissionService;
    let getTextSubmissionForExerciseWithoutAssessmentStub: SinonStub;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;

    const exercise = { id: 20, type: ExerciseType.TEXT, course: { id: 1 } } as TextExercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, TranslateModule.forRoot(), RouterTestingModule.withRoutes([textAssessmentRoutes[0]])],
            declarations: [
                TextAssessmentComponent,
                MockComponent(ResultComponent),
                MockComponent(TextAssessmentEditorComponent),
                MockComponent(ResizableInstructionsComponent),
                MockComponent(AssessmentDetailComponent),
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
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextAssessmentComponent);
                comp = fixture.componentInstance;
                comp.exercise = exercise;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                getTextSubmissionForExerciseWithoutAssessmentStub = stub(textSubmissionService, 'getTextSubmissionForExerciseWithoutAssessment');

                router.initialNavigation();
            });
    });

    afterEach(() => {
        getTextSubmissionForExerciseWithoutAssessmentStub.restore();
    });

    it(
        'AssessNextButton should be visible, the method assessNextOptimal should be invoked ' + 'and the url should change after clicking on the button',
        fakeAsync(() => {
            // set all attributes for comp
            comp.ngOnInit();
            tick();

            comp.userId = 99;
            comp.submission = {
                submissionExerciseType: SubmissionExerciseType.TEXT,
                id: 2278,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: moment('2019-07-09T10:47:33.244Z'),
                text: 'asdfasdfasdfasdf',
            } as TextSubmission;
            comp.result = new Result();
            comp.result.id = 2374;
            comp.result.resultString = '1 of 12 points';
            comp.result.completionDate = moment('2019-07-09T11:51:23.251Z');
            comp.result.successful = false;
            comp.result.score = 8;
            comp.result.rated = true;
            comp.result.hasFeedback = false;
            comp.result.submission = comp.submission;
            comp.isAssessor = true;
            comp.isAtLeastInstructor = true;
            comp.assessmentsAreValid = true;
            const unassessedSubmission = { submissionExerciseType: 'text', id: 2279, submitted: true, type: 'MANUAL' };

            fixture.detectChanges();

            // check if assessNextButton is available
            const assessNextButton = debugElement.query(By.css('#assessNextButton'));
            expect(assessNextButton).to.exist;

            // check if getTextSubmissionForExerciseWithoutAssessment() is called and works
            getTextSubmissionForExerciseWithoutAssessmentStub.returns(of(unassessedSubmission));
            assessNextButton.nativeElement.click();
            expect(getTextSubmissionForExerciseWithoutAssessmentStub).to.have.been.called;
            expect(comp.unassessedSubmission).to.be.deep.equal(unassessedSubmission);

            // check if the url changes when you clicked on assessNextAssessmentButton
            tick();
            expect(location.path()).to.be.equal(
                `/course-management/${comp.exercise.course?.id}/text-exercises/${comp.exercise.id}/submissions/${comp.unassessedSubmission.id}/assessment`,
            );

            fixture.destroy();
            flush();
        }),
    );
});
