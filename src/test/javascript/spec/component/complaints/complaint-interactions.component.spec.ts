import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { Exercise } from 'app/entities/exercise.model';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComplaintsComponent } from 'app/complaints/complaints.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Exam } from 'app/entities/exam.model';
import { Submission } from 'app/entities/submission.model';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { Complaint } from 'app/entities/complaint.model';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ComplaintInteractionsComponent', () => {
    const exercise: Exercise = { id: 1, teamMode: false, course: { id: 1, complaintsEnabled: true, maxComplaintTimeDays: 7 } as Course } as Exercise;
    const submission: Submission = {} as Submission;
    const result: Result = { id: 1, completionDate: moment('2021-07-30T22:17:29.203+02:00') } as Result;
    const participation: Participation = { id: 2, results: [result], submissions: [submission] } as Participation;
    const exam: Exam = {} as Exam;
    const complaint = new Complaint();

    let component: ComplaintInteractionsComponent;
    let fixture: ComponentFixture<ComplaintInteractionsComponent>;
    let complaintService: ComplaintService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [ComplaintInteractionsComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ComplaintsComponent)],
            providers: [
                {
                    provide: ComplaintService,
                    useClass: MockComplaintService,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintInteractionsComponent);
                component = fixture.componentInstance;
                complaintService = TestBed.inject(ComplaintService);
                component.exercise = exercise;
                component.participation = participation;
                component.result = result;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize in exam mode', () => {
        component.exam = exam;
        const complaintResultStub = sinon.stub(complaintService, 'findByResultId').returns(of({ body: complaint } as EntityResponseType));

        fixture.detectChanges();

        expect(component.hasComplaint).to.be.true;
        expect(component.result.participation).to.deep.equal(participation);
        expect(complaintResultStub).to.have.been.calledOnce;
    });

    it('should initialize in not-exam mode', () => {
        const numberOfAllowedComplaints = 10;
        const complaintResultStub = sinon.stub(complaintService, 'findByResultId').returns(of({ body: complaint } as EntityResponseType));
        const complaintsStub = sinon.stub(complaintService, 'getNumberOfAllowedComplaintsInCourse').returns(of(numberOfAllowedComplaints));

        fixture.detectChanges();

        expect(component.hasComplaint).to.be.true;
        expect(component.result.participation).to.deep.equal(participation);
        expect(complaintResultStub).to.have.been.calledOnce;
        expect(complaintsStub).to.have.been.calledOnce;
    });

    it('should check if there were valid complaints submitted in review period for different cases', () => {
        component.hasComplaint = false;

        let getResult = component.noValidComplaintWasSubmittedWithinTheStudentReviewPeriod;

        expect(getResult).to.be.true;

        component.exercise.assessmentDueDate = result.completionDate!.subtract(1, 'days');

        getResult = component.noValidComplaintWasSubmittedWithinTheStudentReviewPeriod;

        expect(getResult).to.be.true;
    });

    it('should check if there were valid feedback requests submitted in review period for different cases', () => {
        let getResult = component.isTimeOfFeedbackRequestValid;

        expect(getResult).to.be.false;

        component.exercise.assessmentDueDate = result.completionDate!.subtract(1, 'days');

        getResult = component.isTimeOfFeedbackRequestValid;

        expect(getResult).to.be.false;

        component.exam = exam;

        getResult = component.isTimeOfFeedbackRequestValid;

        expect(getResult).to.be.false;
    });

    it('should toggle show complaints', () => {
        component.showComplaintForm = false;

        component.toggleComplaintForm();

        expect(component.showRequestMoreFeedbackForm).to.be.false;
        expect(component.showComplaintForm).to.be.true;
    });

    it('should toggle show more feedback requests', () => {
        component.showRequestMoreFeedbackForm = false;

        component.toggleRequestMoreFeedbackForm();

        expect(component.showComplaintForm).to.be.false;
        expect(component.showRequestMoreFeedbackForm).to.be.true;
    });
});
