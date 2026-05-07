import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TutorParticipation, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorParticipationGraphComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TutorParticipationGraphComponent;
    let fixture: ComponentFixture<TutorParticipationGraphComponent>;
    const router = new MockRouter();
    const navigateSpy = vi.spyOn(router, 'navigate');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(TutorParticipationGraphComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /**
     * Helper to set the inputs needed by the participation status tests so the component is
     * always in a fully-initialized state before assertions.
     */
    function setBaseInputs(status: TutorParticipationStatus, exercise: Exercise, participation: TutorParticipation) {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('tutorParticipation', { ...participation, status });
    }

    describe('Participation Status Method', () => {
        const baseExercise = { id: 1, exampleSubmissions: [{ id: 1, usedForTutorial: true }] } as Exercise;
        const baseParticipation = { id: 1, trainedExampleSubmissions: [{ id: 1, usedForTutorial: true }] } as TutorParticipation;

        it('should calculate the right classes for the initial NOT_PARTICIPATED status', () => {
            setBaseInputs(TutorParticipationStatus.NOT_PARTICIPATED, baseExercise, baseParticipation);

            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('active');
            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('opaque');
        });

        it('should calculate the right classes for the REVIEWED_INSTRUCTIONS status', () => {
            setBaseInputs(TutorParticipationStatus.REVIEWED_INSTRUCTIONS, baseExercise, baseParticipation);

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('active');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('');
        });

        it('should calculate the right classes for the TRAINED status', () => {
            setBaseInputs(TutorParticipationStatus.TRAINED, baseExercise, baseParticipation);

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('');

            const exerciseWithMissingReview = {
                id: 1,
                exampleSubmissions: [
                    { id: 1, usedForTutorial: false },
                    { id: 2, usedForTutorial: true },
                ],
            } as Exercise;
            const partialParticipation = { id: 1, trainedExampleSubmissions: [{ id: 1, usedForTutorial: false }], status: TutorParticipationStatus.TRAINED } as TutorParticipation;
            fixture.componentRef.setInput('exercise', exerciseWithMissingReview);
            fixture.componentRef.setInput('tutorParticipation', partialParticipation);

            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('orange');
        });

        it('should calculate the right classes for the COMPLETED status', () => {
            setBaseInputs(TutorParticipationStatus.COMPLETED, baseExercise, baseParticipation);

            expect(comp.calculateClasses(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.NOT_PARTICIPATED)).toBe('');
            expect(comp.calculateClasses(TutorParticipationStatus.TRAINED)).toBe('');
        });
    });

    describe('progressBarClass', () => {
        it('returns active for the COMPLETED status', () => {
            fixture.componentRef.setInput('tutorParticipation', { status: TutorParticipationStatus.COMPLETED } as TutorParticipation);
            expect(comp.progressBarClass()).toBe('active');
        });

        it('returns opaque for the NOT_PARTICIPATED status', () => {
            fixture.componentRef.setInput('tutorParticipation', { status: TutorParticipationStatus.NOT_PARTICIPATED } as TutorParticipation);
            expect(comp.progressBarClass()).toBe('opaque');
        });

        it('returns orange when TRAINED with open complaints', () => {
            fixture.componentRef.setInput('tutorParticipation', { status: TutorParticipationStatus.TRAINED } as TutorParticipation);
            const submissions = new DueDateStat();
            submissions.inTime = 4;
            fixture.componentRef.setInput('numberOfSubmissions', submissions);
            fixture.componentRef.setInput('totalNumberOfAssessments', 5);
            fixture.componentRef.setInput('numberOfOpenComplaints', 1);
            fixture.componentRef.setInput('numberOfOpenMoreFeedbackRequests', 2);

            expect(comp.progressBarClass()).toBe('orange');
        });
    });

    describe('percentageAssessmentProgress', () => {
        it('computes inTime and late progress per correction round', () => {
            fixture.componentRef.setInput('tutorParticipation', { status: TutorParticipationStatus.TRAINED } as TutorParticipation);
            const submissions = new DueDateStat();
            submissions.inTime = 4;
            submissions.late = 2;
            fixture.componentRef.setInput('numberOfSubmissions', submissions);
            fixture.componentRef.setInput('totalNumberOfAssessments', 3);

            const round1 = new DueDateStat();
            round1.inTime = 3;
            round1.late = 1;
            fixture.componentRef.setInput('numberOfAssessmentsOfCorrectionRounds', [round1]);

            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound()[0]).toBe(75);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound()[0]).toBe(50);

            const round2 = new DueDateStat();
            round2.inTime = 2;
            round2.late = 1;
            fixture.componentRef.setInput('numberOfAssessmentsOfCorrectionRounds', [round1, round2]);

            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound()[0]).toBe(75);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound()[0]).toBe(50);
            expect(comp.percentageInTimeAssessmentProgressOfCorrectionRound()[1]).toBe(50);
            expect(comp.percentageLateAssessmentProgressOfCorrectionRound()[1]).toBe(50);
        });
    });

    it('derives the routerLink from the trained example submissions exercise/course', () => {
        fixture.componentRef.setInput('tutorParticipation', {
            id: 1,
            trainedExampleSubmissions: [{ id: 1, usedForTutorial: false, exercise: { id: 1, course: { id: 3 } } }],
            status: TutorParticipationStatus.TRAINED,
        } as TutorParticipation);

        expect(comp.routerLink()).toBe('/course-management/3/assessment-dashboard/1');
    });

    it('computes the complaints numerator', () => {
        fixture.componentRef.setInput('tutorParticipation', { status: TutorParticipationStatus.TRAINED } as TutorParticipation);
        fixture.componentRef.setInput('numberOfComplaints', 2);
        fixture.componentRef.setInput('numberOfOpenComplaints', 1);
        fixture.componentRef.setInput('numberOfMoreFeedbackRequests', 3);
        fixture.componentRef.setInput('numberOfOpenMoreFeedbackRequests', 1);

        expect(comp.complaintsNumerator()).toBe(3);
    });

    it('computes the complaints denominator', () => {
        fixture.componentRef.setInput('tutorParticipation', { status: TutorParticipationStatus.TRAINED } as TutorParticipation);
        fixture.componentRef.setInput('numberOfComplaints', 3);
        fixture.componentRef.setInput('numberOfMoreFeedbackRequests', 4);

        expect(comp.complaintsDenominator()).toBe(7);
    });

    it('reactively reflects updated tutorParticipation status', () => {
        fixture.componentRef.setInput('tutorParticipation', { id: 1, status: TutorParticipationStatus.TRAINED } as TutorParticipation);
        expect(comp.tutorParticipationStatus()).toBe(TutorParticipationStatus.TRAINED);

        fixture.componentRef.setInput('tutorParticipation', { id: 3, status: TutorParticipationStatus.COMPLETED } as TutorParticipation);
        expect(comp.tutorParticipationStatus()).toBe(TutorParticipationStatus.COMPLETED);
    });

    it('computes percentageComplaintsProgress', () => {
        fixture.componentRef.setInput('tutorParticipation', { status: TutorParticipationStatus.TRAINED } as TutorParticipation);
        fixture.componentRef.setInput('numberOfComplaints', 10);
        fixture.componentRef.setInput('numberOfOpenComplaints', 5);
        fixture.componentRef.setInput('numberOfMoreFeedbackRequests', 2);
        fixture.componentRef.setInput('numberOfOpenMoreFeedbackRequests', 1);

        expect(comp.percentageComplaintsProgress()).toBe(50);
    });

    it('navigates to the routerLink', () => {
        fixture.componentRef.setInput('tutorParticipation', {
            id: 1,
            trainedExampleSubmissions: [{ id: 1, usedForTutorial: false, exercise: { id: 9, course: { id: 7 } } }],
            status: TutorParticipationStatus.TRAINED,
        } as TutorParticipation);

        comp.navigate();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management/7/assessment-dashboard/9']);
    });
});
