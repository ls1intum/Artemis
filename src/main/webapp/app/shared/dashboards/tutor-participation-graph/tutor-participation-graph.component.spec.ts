import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TutorParticipation, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { MockComponent } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorParticipationGraphComponent', () => {
    let comp: TutorParticipationGraphComponent;
    let fixture: ComponentFixture<TutorParticipationGraphComponent>;
    let calculatePercentageAssessmentProgressStub: jest.SpyInstance;
    let calculatePercentageComplaintsProgressStub: jest.SpyInstance;
    const router = new MockRouter();
    const navigateSpy = jest.spyOn(router, 'navigate');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [TutorParticipationGraphComponent, MockComponent(ProgressBarComponent), TranslatePipeMock],
            providers: [
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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

            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('active');
            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('opaque');
        });

        it('should calculate the right classes for the REVIEWED_INSTRUCTIONS status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('active');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('');
        });

        it('should calculate the right classes for the TRAINED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('');

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

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('orange');
        });

        it('should calculate the right classes for the COMPLETED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.COMPLETED;

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('');
        });
    });

    describe('should calculate the right classes for the given status', () => {
        it('should calculate the right classes for the COMPLETED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.COMPLETED;
            expect(comp.calculateClassProgressBar()).toBe('active');
        });

        it('should calculate the right classes for the NOT_PARTICIPATED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;
            expect(comp.calculateClassProgressBar()).toBe('opaque');
        });

        it('should calculate the right classes for the TRAINED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;
            comp.numberOfSubmissions = new DueDateStat();
            comp.numberOfSubmissions.inTime = 4;
            comp.totalNumberOfAssessments = 5;
            comp.numberOfOpenComplaints = 1;
            comp.numberOfOpenMoreFeedbackRequests = 2;

            expect(comp.calculateClassProgressBar()).toBe('orange');
        });
    });

    describe('test calculatePercentageAssessmentProgress', () => {
        it('should calculate the right classes for the TRAINED status', () => {
            comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;
            comp.numberOfSubmissions = new DueDateStat();
            comp.numberOfSubmissions.inTime = 4;
            comp.numberOfSubmissions.late = 2;
            comp.totalNumberOfAssessments = 3;
            const firstCorrectionDueDateStat = new DueDateStat();
            firstCorrectionDueDateStat.inTime = comp.totalNumberOfAssessments;
            firstCorrectionDueDateStat.late = 1;
            comp.numberOfAssessmentsOfCorrectionRounds = [firstCorrectionDueDateStat];
            comp.numberOfOpenComplaints = 1;
            comp.numberOfOpenMoreFeedbackRequests = 2;

            comp.calculatePercentageAssessmentProgress();

            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound[0]).toBe(75);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound[0]).toBe(50);

            const secondCorrectionDueDateStat = new DueDateStat();
            secondCorrectionDueDateStat.inTime = 2;
            secondCorrectionDueDateStat.late = 1;
            comp.numberOfAssessmentsOfCorrectionRounds = [firstCorrectionDueDateStat, secondCorrectionDueDateStat];

            comp.calculatePercentageAssessmentProgress();
            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound[0]).toBe(75);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound[0]).toBe(50);
            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound[1]).toBe(50);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound[1]).toBe(50);
        });
    });

    it('should test ngOnInit', () => {
        calculatePercentageAssessmentProgressStub = jest.spyOn(comp, 'calculatePercentageAssessmentProgress').mockImplementation();
        calculatePercentageComplaintsProgressStub = jest.spyOn(comp, 'calculatePercentageComplaintsProgress').mockImplementation();
        comp.tutorParticipation = {
            id: 1,
            trainedExampleSubmissions: [{ id: 1, usedForTutorial: false, exercise: { id: 1, course: { id: 3 } } }],
        } as TutorParticipation;

        comp.ngOnInit();

        expect(calculatePercentageAssessmentProgressStub).toHaveBeenCalledOnce();
        expect(calculatePercentageComplaintsProgressStub).toHaveBeenCalledOnce();

        calculatePercentageAssessmentProgressStub.mockRestore();
        calculatePercentageComplaintsProgressStub.mockRestore();
    });

    it('should calculate numerator', () => {
        comp.numberOfComplaints = 2;
        comp.numberOfOpenComplaints = 1;
        comp.numberOfMoreFeedbackRequests = 3;
        comp.numberOfOpenMoreFeedbackRequests = 1;

        const result = comp.calculateComplaintsNumerator();

        expect(result).toBe(3);
    });

    it('should calculate denominator', () => {
        comp.numberOfComplaints = 3;
        comp.numberOfMoreFeedbackRequests = 4;

        const result = comp.calculateComplaintsDenominator();

        expect(result).toBe(7);
    });
    it('should update changes', () => {
        calculatePercentageAssessmentProgressStub = jest.spyOn(comp, 'calculatePercentageAssessmentProgress').mockImplementation();
        calculatePercentageComplaintsProgressStub = jest.spyOn(comp, 'calculatePercentageComplaintsProgress').mockImplementation();

        const tutorParticipationStatus = { status: 'COMPLETED' as TutorParticipationStatus };
        const unchangedParticipation = { id: 1, trainedExampleSubmissions: [{ id: 1, usedForTutorial: true }], tutorParticipationStatus } as TutorParticipation;
        const changedParticipation = { id: 3, trainedExampleSubmissions: [{ id: 1, usedForTutorial: true }], tutorParticipationStatus } as TutorParticipation;
        comp.tutorParticipation = unchangedParticipation;
        comp.ngOnInit();

        expect(comp.tutorParticipation.id).not.toBe(3);

        const changes = { tutorParticipation: { currentValue: changedParticipation } as SimpleChange };
        comp.ngOnChanges(changes);

        expect(comp.tutorParticipation.id).toBe(3);
        expect(calculatePercentageAssessmentProgressStub).toHaveBeenCalledTimes(2);
        expect(calculatePercentageComplaintsProgressStub).toHaveBeenCalledTimes(2);

        calculatePercentageAssessmentProgressStub.mockRestore();
        calculatePercentageComplaintsProgressStub.mockRestore();
    });

    it('should calculatePercentageComplaintsProgress', () => {
        comp.tutorParticipationStatus = TutorParticipationStatus.TRAINED;
        comp.numberOfComplaints = 10;
        comp.numberOfOpenComplaints = 5;
        comp.numberOfMoreFeedbackRequests = 2;
        comp.numberOfOpenMoreFeedbackRequests = 1;

        comp.calculatePercentageComplaintsProgress();

        expect(comp.percentageComplaintsProgress).toBe(50);
    });

    it('should navigate', () => {
        comp.routerLink = `url`;
        comp.navigate();
        expect(navigateSpy).toHaveBeenCalledWith([`url`]);
    });
});
