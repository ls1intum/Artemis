import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { Exercise } from 'app/entities/exercise.model';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import * as sinon from 'sinon';
import { MockComponent, MockDirective } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('TutorParticipationGraphComponent', () => {
    let comp: TutorParticipationGraphComponent;
    let fixture: ComponentFixture<TutorParticipationGraphComponent>;
    let calculatePercentageAssessmentProgressStub: SinonStub;
    let calculatePercentageComplaintsProgressStub: SinonStub;
    const router = new MockRouter();
    const navigateSpy = sinon.spy(router, 'navigate');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TutorParticipationGraphComponent, MockComponent(ProgressBarComponent), TranslatePipeMock, MockDirective(NgbTooltip)],
            providers: [{ provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorParticipationGraphComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('Participation Status Method', () => {
        beforeEach(() => {
            comp.exercise = {
                id: 1,
                exampleSubmissions: [{ id: 1, usedForTutorial: true }],
            } as Exercise;

            comp.tutorParticipation = {
                id: 1,
                trainedExampleSubmissions: [{ id: 1, usedForTutorial: true }],
            } as TutorParticipation;
        });

        it('should calculate the right classes for the initial NOT_PARTICIPATED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;

            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('active');
            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('opaque');
        });

        it('should calculate the right classes for the REVIEWED_INSTRUCTIONS status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('active');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('');
        });

        it('should calculate the right classes for the TRAINED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('');

            comp.exercise = {
                id: 1,
                exampleSubmissions: [
                    { id: 1, usedForTutorial: false },
                    { id: 2, usedForTutorial: true },
                ],
            } as Exercise;

            comp.tutorParticipation = {
                id: 1,
                trainedExampleSubmissions: [{ id: 1, usedForTutorial: false }],
            } as TutorParticipation;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('orange');
        });

        it('should calculate the right classes for the COMPLETED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.COMPLETED;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).to.equal('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).to.equal('');
        });
    });

    describe('should calculate the right classes for the given status', () => {
        it('should calculate the right classes for the COMPLETED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.COMPLETED;
            expect(comp.calculateClassProgressBar()).to.equal('active');
        });

        it('should calculate the right classes for the NOT_PARTICIPATED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;
            expect(comp.calculateClassProgressBar()).to.equal('opaque');
        });

        it('should calculate the right classes for the TRAINED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;
            comp.numberOfSubmissions = new DueDateStat();
            comp.numberOfSubmissions.inTime = 4;
            comp.totalNumberOfAssessments = new DueDateStat();
            comp.totalNumberOfAssessments.inTime = 5;
            comp.numberOfOpenComplaints = 1;
            comp.numberOfOpenMoreFeedbackRequests = 2;

            expect(comp.calculateClassProgressBar()).to.equal('orange');
        });
    });

    describe('test calculatePercentageAssessmentProgress', () => {
        it('should calculate the right classes for the TRAINED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;
            comp.numberOfSubmissions = new DueDateStat();
            comp.numberOfSubmissions.inTime = 4;
            comp.numberOfSubmissions.late = 2;
            comp.totalNumberOfAssessments = new DueDateStat();
            comp.numberOfAssessmentsOfCorrectionRounds = [comp.totalNumberOfAssessments];
            comp.totalNumberOfAssessments.inTime = 3;
            comp.totalNumberOfAssessments.late = 1;
            comp.numberOfOpenComplaints = 1;
            comp.numberOfOpenMoreFeedbackRequests = 2;

            comp.calculatePercentageAssessmentProgress();

            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound[0]).to.equal(75);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound[0]).to.equal(50);

            const secondCorrectionDueDateStat = new DueDateStat();
            secondCorrectionDueDateStat.inTime = 2;
            secondCorrectionDueDateStat.late = 1;
            comp.numberOfAssessmentsOfCorrectionRounds = [comp.totalNumberOfAssessments, secondCorrectionDueDateStat];

            comp.calculatePercentageAssessmentProgress();
            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound[0]).to.equal(75);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound[0]).to.equal(50);
            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound[1]).to.equal(50);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound[1]).to.equal(50);
        });
    });

    it('should test ngOnInit', () => {
        calculatePercentageAssessmentProgressStub = stub(comp, 'calculatePercentageAssessmentProgress');
        calculatePercentageComplaintsProgressStub = stub(comp, 'calculatePercentageComplaintsProgress');
        comp.tutorParticipation = {
            id: 1,
            trainedExampleSubmissions: [{ id: 1, usedForTutorial: false, exercise: { id: 1, course: { id: 3 } } }],
        } as TutorParticipation;

        comp.ngOnInit();

        expect(calculatePercentageAssessmentProgressStub).to.have.been.calledOnce;
        expect(calculatePercentageComplaintsProgressStub).to.have.been.calledOnce;

        calculatePercentageAssessmentProgressStub.restore();
        calculatePercentageComplaintsProgressStub.restore();
    });

    it('should calculate numerator', () => {
        comp.numberOfComplaints = 2;
        comp.numberOfOpenComplaints = 1;
        comp.numberOfMoreFeedbackRequests = 3;
        comp.numberOfOpenMoreFeedbackRequests = 1;

        const result = comp.calculateComplaintsNumerator();

        expect(result).to.equal(3);
    });

    it('should calculate denominator', () => {
        comp.numberOfComplaints = 3;
        comp.numberOfMoreFeedbackRequests = 4;

        const result = comp.calculateComplaintsDenominator();

        expect(result).to.equal(7);
    });
    it('should update changes', () => {
        calculatePercentageAssessmentProgressStub = stub(comp, 'calculatePercentageAssessmentProgress');
        calculatePercentageComplaintsProgressStub = stub(comp, 'calculatePercentageComplaintsProgress');

        const tutorParticipationStatus = { status: 'COMPLETED' as TutorParticipationStatus };
        const unchangedParticipation = { id: 1, trainedExampleSubmissions: [{ id: 1, usedForTutorial: true }], tutorParticipationStatus } as TutorParticipation;
        const changedParticipation = { id: 3, trainedExampleSubmissions: [{ id: 1, usedForTutorial: true }], tutorParticipationStatus } as TutorParticipation;
        comp.tutorParticipation = unchangedParticipation;
        comp.ngOnInit();

        expect(comp.tutorParticipation.id).to.not.equal(3);

        const changes = { tutorParticipation: { currentValue: changedParticipation } as SimpleChange };
        comp.ngOnChanges(changes);

        expect(comp.tutorParticipation.id).to.equal(3);
        expect(calculatePercentageAssessmentProgressStub).to.have.been.calledTwice;
        expect(calculatePercentageComplaintsProgressStub).to.have.been.calledTwice;

        calculatePercentageAssessmentProgressStub.restore();
        calculatePercentageComplaintsProgressStub.restore();
    });

    it('should calculatePercentageComplaintsProgress', () => {
        comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;
        comp.numberOfComplaints = 10;
        comp.numberOfOpenComplaints = 5;
        comp.numberOfMoreFeedbackRequests = 2;
        comp.numberOfOpenMoreFeedbackRequests = 1;

        comp.calculatePercentageComplaintsProgress();

        expect(comp.percentageComplaintsProgress).to.equal(50);
    });

    it('should navigate', () => {
        comp.routerLink = `url`;
        comp.navigate();
        expect(navigateSpy).to.have.been.calledWith([`url`]);
    });
});
