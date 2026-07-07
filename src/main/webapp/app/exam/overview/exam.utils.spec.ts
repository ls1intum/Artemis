import {
    endTime,
    examWorkingTime,
    getAdditionalWorkingTime,
    getRelativeWorkingTimeExtension,
    isExamOverMultipleDays,
    isExamResultPublished,
    normalWorkingTime,
} from 'app/exam/overview/exam.utils';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import dayjs from 'dayjs/esm';

import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

let artemisServerDateService: ArtemisServerDateService;

describe('ExamUtils', () => {
    setupTestBed({ zoneless: true });

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: ArtemisServerDateService, useClass: MockArtemisServerDateService }],
        })
            .compileComponents()
            .then(() => {
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
            });
    });

    describe('endTime', () => {
        it('should return undefined when exam is missing', () => {
            expect(endTime(undefined!, { workingTime: 3600 } as StudentExam)).toBeUndefined();
        });

        it('should return exam end date when student working time is unavailable', () => {
            const examEndDate = dayjs('2026-07-01T12:00:00Z');
            const exam = { startDate: dayjs('2026-07-01T10:00:00Z'), endDate: examEndDate } as Exam;

            expect(endTime(exam, {} as StudentExam)).toEqual(examEndDate);
        });

        it('should return personal end date based on student working time', () => {
            const startDate = dayjs('2026-07-01T10:00:00Z');
            const exam = { startDate, endDate: dayjs('2026-07-01T11:00:00Z') } as Exam;
            const studentExam = { workingTime: 7200 } as StudentExam;

            expect(endTime(exam, studentExam)?.toISOString()).toBe(startDate.add(7200, 'seconds').toISOString());
        });
    });

    describe('normalWorkingTime', () => {
        it('should return undefined when either date is missing', () => {
            const startDate = dayjs('2026-07-01T10:00:00Z');

            expect(normalWorkingTime(undefined, startDate)).toBeUndefined();
            expect(normalWorkingTime(startDate, undefined)).toBeUndefined();
        });

        it('should return the difference in seconds between start and end date', () => {
            const startDate = dayjs('2026-07-01T10:00:00Z');
            const endDate = dayjs('2026-07-01T11:30:00Z');

            expect(normalWorkingTime(startDate, endDate)).toBe(5400);
        });
    });

    describe('examWorkingTime', () => {
        it('should delegate to normalWorkingTime using exam dates', () => {
            const exam = {
                startDate: dayjs('2026-07-01T10:00:00Z'),
                endDate: dayjs('2026-07-01T12:00:00Z'),
            } as Exam;

            expect(examWorkingTime(exam)).toBe(7200);
        });
    });

    describe('getAdditionalWorkingTime', () => {
        it('should return zero when required data is missing', () => {
            expect(getAdditionalWorkingTime({} as Exam, {} as StudentExam)).toBe(0);
        });

        it('should return the extra seconds beyond the regular exam end date', () => {
            const exam = {
                startDate: dayjs('2026-07-01T10:00:00Z'),
                endDate: dayjs('2026-07-01T11:00:00Z'),
            } as Exam;
            const studentExam = { workingTime: 5400 } as StudentExam;

            expect(getAdditionalWorkingTime(exam, studentExam)).toBe(1800);
        });
    });

    describe('getRelativeWorkingTimeExtension', () => {
        it('should calculate the relative extension in percent points', () => {
            const exam = {
                startDate: dayjs('2026-07-01T10:00:00Z'),
                endDate: dayjs('2026-07-01T11:00:00Z'),
            } as Exam;

            expect(getRelativeWorkingTimeExtension(exam, 5400)).toBe(50);
        });
    });

    describe('isExamOverMultipleDays', () => {
        it('should return false when exam dates are missing', () => {
            expect(isExamOverMultipleDays({} as Exam, {} as StudentExam)).toBe(false);
        });

        it('should return true when the effective end date is on another day', () => {
            const exam = {
                startDate: dayjs('2026-07-01T10:00:00'),
                endDate: dayjs('2026-07-01T12:00:00'),
            } as Exam;
            const studentExam = { workingTime: 86400 } as StudentExam;

            expect(isExamOverMultipleDays(exam, studentExam)).toBe(true);
        });

        it('should return false when the effective end date is on the same day', () => {
            const exam = {
                startDate: dayjs('2026-07-01T10:00:00Z'),
                endDate: dayjs('2026-07-01T12:00:00Z'),
            } as Exam;
            const studentExam = { workingTime: 3600 } as StudentExam;

            expect(isExamOverMultipleDays(exam, studentExam)).toBe(false);
        });
    });

    describe('isExamResultPublished', () => {
        it('should always be true for test runs', () => {
            const isTestRun = true;
            const exam = undefined;

            const resultsArePublished = isExamResultPublished(isTestRun, exam, artemisServerDateService);
            expect(resultsArePublished).toBe(true);
        });

        it('should be false if publishReleaseDate is in the future', () => {
            const isTestRun = false;
            const dateInFuture = dayjs().add(5, 'days');
            const exam = { publishResultsDate: dateInFuture } as Exam;

            const resultsArePublished = isExamResultPublished(isTestRun, exam, artemisServerDateService);
            expect(resultsArePublished).toBe(false);
        });

        it('should be true if publishReleaseDate is in the past', () => {
            const isTestRun = false;
            const dateInPast = dayjs().subtract(2, 'days');
            const exam = { publishResultsDate: dateInPast } as Exam;

            const resultsArePublished = isExamResultPublished(isTestRun, exam, artemisServerDateService);
            expect(resultsArePublished).toBe(true);
        });

        it('should be falsy when publishResultsDate is missing', () => {
            expect(isExamResultPublished(false, {} as Exam, artemisServerDateService)).toBeFalsy();
            expect(isExamResultPublished(false, undefined, artemisServerDateService)).toBeFalsy();
        });
    });
});
