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
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { TextSubmission } from 'app/entities/text-submission';
import { TextAssessmentEditorComponent } from 'app/text-assessment/text-assessment-editor/text-assessment-editor.component';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { TextAssessmentDetailComponent } from 'app/text-assessment/text-assessment-detail/text-assessment-detail.component';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { ParticipationSubmissionComponent } from 'app/entities/participation-submission/participation-submission.component';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { participationSubmissionRoute } from 'app/entities/participation-submission';

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
            imports: [ArtemisTestModule, NgxDatatableModule, ArtemisResultModule, ArtemisSharedModule, RouterTestingModule.withRoutes([participationSubmissionRoute[0]])],
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

    it('Submissions are correctly loaded from server', fakeAsync(() => {
        // set all attributes for comp
        const submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.TEXT,
                id: 2278,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: moment('2019-07-09T10:47:33.244Z'),
                text: 'asdfasdfasdfasdf',
                participation: { id: 1 },
            },
        ] as TextSubmission[];

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
