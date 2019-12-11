import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../mocks';
import { ResultComponent } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared';
import { ExerciseType } from 'app/entities/exercise';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { TextAssessmentComponent } from 'app/text-assessment/text-assessment.component';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise } from 'app/entities/text-exercise';
import { TextAssessmentEditorComponent } from 'app/text-assessment/text-assessment-editor/text-assessment-editor.component';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { AssessmentDetailComponent } from 'app/assessment-shared/assessment-detail/assessment-detail.component';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { textAssessmentRoutes } from 'app/text-assessment/text-assessment.route';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { TranslateModule } from '@ngx-translate/core';

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

    const exercise = { id: 20, type: ExerciseType.TEXT } as TextExercise;

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
                { provide: JhiAlertService, useClass: MockAlertService },
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
                textSubmissionService = TestBed.get(TextSubmissionService);
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
            expect(location.path()).to.be.equal('/text/' + comp.exercise.id + '/assessment/' + comp.unassessedSubmission.id);

            fixture.destroy();
            flush();
        }),
    );
});
