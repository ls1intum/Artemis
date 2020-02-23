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
import { TextAssessmentEditorComponent } from 'app/exercises/text/assess/text-assessment/text-assessment-editor/text-assessment-editor.component';
import { ResizableInstructionsComponent } from 'app/exercises/text/assess/text-assessment/resizable-instructions/resizable-instructions.component';
import { AssessmentDetailComponent } from 'app/assessment/assessment-detail/assessment-detail.component';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TranslateModule } from '@ngx-translate/core';
import { participationSubmissionRoute } from 'app/exercises/shared/participation-submission/participation-submission.route';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { UpdatingResultComponent } from 'app/shared/result/updating-result.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text-submission.model';

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
            imports: [
                ArtemisTestModule,
                NgxDatatableModule,
                ArtemisResultModule,
                ArtemisSharedModule,
                TranslateModule.forRoot(),
                RouterTestingModule.withRoutes([participationSubmissionRoute[0]]),
            ],
            declarations: [
                ParticipationSubmissionComponent,
                MockComponent(UpdatingResultComponent),
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
                fixture = TestBed.createComponent(ParticipationSubmissionComponent);
                comp = fixture.componentInstance;
                comp.participationId = 1;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);
                submissionService = TestBed.inject(SubmissionService);
                findAllSubmissionsOfParticipationStub = stub(submissionService, 'findAllSubmissionsOfParticipation');
                router.initialNavigation();
            });
    });

    afterEach(() => {
        findAllSubmissionsOfParticipationStub.restore();
    });

    it('Submissions are correctly loaded from server', fakeAsync(() => {
        // set all attributes for comp
        const participation = new StudentParticipation();
        participation.id = 1;
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
        submissions[0].participation = participation;

        // check if findAllSubmissionsOfParticipationStub() is called and works
        findAllSubmissionsOfParticipationStub.returns(of({ body: submissions }));
        fixture.detectChanges();
        comp.ngOnInit();
        tick();
        expect(findAllSubmissionsOfParticipationStub).to.have.been.called;
        expect(comp.submissions).to.be.deep.equal(submissions);

        // check if delete button is available
        const deleteButton = debugElement.query(By.css('#deleteButton'));
        expect(deleteButton).to.exist;

        // check if the right amount of rows is visible
        const row = debugElement.query(By.css('#participationSubmissionTable'));
        expect(row.childNodes.length).to.equal(1);

        fixture.destroy();
        flush();
    }));
});
