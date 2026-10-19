import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { ParticipationPrivacyService } from './participation-privacy.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

/** Deadline changes are observed without requiring a page reload. */
describe('ParticipationPrivacyService', () => {
    const now = new Date('2026-10-15T12:00:00Z');
    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        studentAssignedTeamIdComputed: false,
        secondCorrectionEnabled: false,
        isAtLeastTutor: true,
        isAtLeastInstructor: false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: ArtemisServerDateService, useValue: { now: () => dayjs() } }] });
        vi.useFakeTimers();
        vi.setSystemTime(now);
    });

    afterEach(() => vi.useRealTimers());

    it.each([
        [AssessmentType.AUTOMATIC, -1, false],
        [AssessmentType.AUTOMATIC, 1, false],
        [AssessmentType.SEMI_AUTOMATIC, -1, true],
        [AssessmentType.SEMI_AUTOMATIC, 1, false],
        [AssessmentType.MANUAL, -1, true],
        [AssessmentType.MANUAL, 1, false],
    ])('assessment %s with deadline offset %s hides identities: %s', (assessmentType, dueOffset, hidden) => {
        const service = TestBed.inject(ParticipationPrivacyService);
        expect(service.isParticipantInformationHidden({ ...exercise, assessmentType, dueDate: dayjs(now).add(dueOffset, 'day') })).toBe(hidden);
    });

    it('keeps support access without a due date', () => {
        const service = TestBed.inject(ParticipationPrivacyService);
        expect(service.isParticipantInformationHidden({ ...exercise, assessmentType: AssessmentType.SEMI_AUTOMATIC })).toBe(false);
    });

    it('switches to anonymous assessment at the deadline using the server clock', () => {
        const service = TestBed.inject(ParticipationPrivacyService);
        const timedExercise = { ...exercise, assessmentType: AssessmentType.SEMI_AUTOMATIC, dueDate: dayjs(now).add(1, 'second') };
        expect(service.isParticipantInformationHidden(timedExercise)).toBe(false);
        vi.advanceTimersByTime(1000);
        expect(service.isParticipantInformationHidden(timedExercise)).toBe(true);
    });

    it('preserves instructor visibility and existing exam and other exercise anonymity', () => {
        const service = TestBed.inject(ParticipationPrivacyService);
        const closedExercise = { ...exercise, assessmentType: AssessmentType.SEMI_AUTOMATIC, dueDate: dayjs(now).subtract(1, 'day') };
        expect(service.isParticipantInformationHidden({ ...closedExercise, isAtLeastInstructor: true })).toBe(false);
        expect(service.isParticipantInformationHidden({ ...exercise, exerciseGroup: { id: 2 } })).toBe(true);
        expect(service.isParticipantInformationHidden({ ...exercise, type: ExerciseType.TEXT })).toBe(true);
    });
});
