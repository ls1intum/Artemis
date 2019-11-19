import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService, StudentParticipation } from 'src/main/webapp/app/entities/participation';
import { Result, ResultService } from 'src/main/webapp/app/entities/result';
import { Feedback } from 'src/main/webapp/app/entities/feedback';
import { MockResultService } from '../../mocks/mock-result.service';
import { ProgrammingExerciseParticipationService } from 'src/main/webapp/app/entities/programming-exercise';
import { RepositoryFileService } from 'src/main/webapp/app/entities/repository';
import { MockRepositoryFileService } from '../../mocks/mock-repository-file.service';
import { MockExerciseHintService, MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { MockNgbModalService } from '../../mocks/mock-ngb-modal.service';
import { MockProgrammingExerciseParticipationService } from '../../mocks/mock-programming-exercise-participation.service';
import { ExerciseHintService } from 'app/entities/exercise-hint';
import { ProgrammingAssessmentManualResultDialogComponent, ProgrammingAssessmentManualResultService } from 'app/programming-assessment/manual-result';
import { Complaint, ComplaintService } from 'app/entities/complaint';
import { ProgrammingSubmission } from 'app/entities/programming-submission';
import { ArtemisSharedModule } from 'app/shared';
import { BuildLogService } from 'app/programming-assessment/build-logs/build-log.service';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { User } from 'app/core';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService, LocalStorageService } from 'ngx-webstorage';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingAssessmentManualResultDialogComponent', () => {
    let comp: ProgrammingAssessmentManualResultDialogComponent;
    let fixture: ComponentFixture<ProgrammingAssessmentManualResultDialogComponent>;
    let debugElement: DebugElement;
    let programmingAssessmentManualResultService: ProgrammingAssessmentManualResultService;
    let complaintService: ComplaintService;
    let accountService: AccountService;

    let updateAfterComplaintStub: SinonStub;
    let findByResultId: SinonStub;
    let getIdentity: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, NgbModule, FormDateTimePickerModule, FormsModule],
            declarations: [ProgrammingAssessmentManualResultDialogComponent, ComplaintsForTutorComponent],
            providers: [
                ProgrammingAssessmentManualResultService,
                ComplaintService,
                BuildLogService,
                AccountService,
                { provide: ResultService, useClass: MockResultService },
                {
                    provide: ProgrammingExerciseParticipationService,
                    useClass: MockProgrammingExerciseParticipationService,
                },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingAssessmentManualResultDialogComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                programmingAssessmentManualResultService = debugElement.injector.get(ProgrammingAssessmentManualResultService);
                complaintService = debugElement.injector.get(ComplaintService);
                accountService = debugElement.injector.get(AccountService);
                updateAfterComplaintStub = stub(programmingAssessmentManualResultService, 'updateAfterComplaint');
                findByResultId = stub(complaintService, 'findByResultId');
                getIdentity = stub(accountService, 'identity');
            });
    });

    afterEach(() => {
        updateAfterComplaintStub.restore();
        findByResultId.restore();
    });

    it('should get complaint for result with complaint and check assessor', fakeAsync(() => {
        let complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?' };
        let result = new Result();
        let user = new User();
        user.id = 1;
        result.assessor = user;
        result.hasComplaint = true;
        result.participation = new StudentParticipation();
        result.feedbacks = [new Feedback()];
        result.submission = new ProgrammingSubmission();
        result.submission.id = 1;
        result.score = 80;
        complaint.result = result;
        findByResultId.returns(of(complaint));
        getIdentity.returns(new Promise(resolve => resolve(user)));
        comp.result = result;
        comp.ngOnInit();
        tick();
        expect(findByResultId.calledOnce).to.be.true;
        expect(comp.isAssessor).to.be.true;
    }));
});
