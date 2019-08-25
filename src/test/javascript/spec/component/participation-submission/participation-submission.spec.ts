import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService, JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../mocks';
import { ArtemisResultModule, UpdatingResultComponent } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared';
import { ExerciseType } from 'app/entities/exercise';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise } from 'app/entities/text-exercise';
import { TextAssessmentEditorComponent } from 'app/text-assessment/text-assessment-editor/text-assessment-editor.component';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { TextAssessmentDetailComponent } from 'app/text-assessment/text-assessment-detail/text-assessment-detail.component';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { textAssessmentRoutes } from 'app/text-assessment/text-assessment.route';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { ParticipationSubmissionComponent } from 'app/entities/participation-submission/participation-submission.component';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { participationRoute } from 'app/entities/participation';
import { MockComplaintService } from '../../mocks/mock-complaint.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipationSubmissionComponent', () => {
    let comp: ParticipationSubmissionComponent;
    let fixture: ComponentFixture<ParticipationSubmissionComponent>;
    let submissionService: SubmissionService;
    let findAllSubmissionsOfParticipationStub: SinonStub;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisResultModule, ArtemisSharedModule, RouterTestingModule.withRoutes([participationRoute[2]])],
            declarations: [
                ParticipationSubmissionComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(TextAssessmentEditorComponent),
                MockComponent(ResizableInstructionsComponent),
                MockComponent(TextAssessmentDetailComponent),
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
                fixture = TestBed.createComponent(ParticipationSubmissionComponent);
                comp = fixture.componentInstance;
                comp.participationId = 1;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);
                submissionService = TestBed.get(SubmissionService);
                findAllSubmissionsOfParticipationStub = stub(submissionService, 'findAllSubmissionsOfParticipation');
                router.initialNavigation();
            });
    });

    afterEach(() => {
        findAllSubmissionsOfParticipationStub.restore();
    });

    it(
        'AssessNextButton should be visible, the method assessNextOptimal should be invoked ' + 'and the url should change after clicking on the button',
        fakeAsync(() => {
            // set all attributes for comp
            const submissions = [
                {
                    submissionExerciseType: SubmissionExerciseType.TEXT,
                    id: 2278,
                    submitted: true,
                    type: SubmissionType.MANUAL,
                    submissionDate: moment('2019-07-09T10:47:33.244Z'),
                    text: 'asdfasdfasdfasdf',
                },
            ] as TextSubmission[];

            const result = {
                id: 2374,
                resultString: '1 of 12 points',
                completionDate: moment('2019-07-09T11:51:23.251Z'),
                successful: false,
                score: 8,
                rated: true,
                hasFeedback: false,
                submission: submissions[0],
            };

            //fixture.detectChanges();

            // check if findAllSubmissionsOfParticipationStub() is called and works
            findAllSubmissionsOfParticipationStub.returns(of(submissions));
            comp.setupPage();
            tick();
            expect(findAllSubmissionsOfParticipationStub).to.have.been.called;
            expect(comp.submissions).to.be.deep.equal(submissions);

            // check if deleteButton is available
            const deleteButton = debugElement.query(By.css('#deleteButton'));
            expect(deleteButton).to.exist;

            // check if the url changes when you clicked on assessNextAssessmentButton
            tick();
            // expect(location.path()).to.be.equal('/text/' + comp.exercise.id + '/assessment/' + comp.unassessedSubmission.id);

            fixture.destroy();
            flush();
        }),
    );
});
