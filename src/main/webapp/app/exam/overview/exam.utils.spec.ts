import { examWorkingTime, isExamResultPublished } from 'app/exam/overview/exam.utils';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import dayjs from 'dayjs/esm';

import { beforeEach, describe, expect, it } from 'vitest';
let artemisServerDateService: ArtemisServerDateService;

describe('ExamUtils', () => {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: ArtemisServerDateService, useClass: MockArtemisServerDateService }],
        })
            .compileComponents()
            .then(() => {
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
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
    });

    describe('examWorkingTime', () => {
        it('should calculate duration between dates for REAL exam', () => {
            const exam = {
                examMode: ExamMode.REAL,
                startDate: dayjs().add(1, 'hour'),
                endDate: dayjs().add(2, 'hours'),
                workingTime: 60, // Should ignore explicit working time and use difference
            } as Exam;
            expect(examWorkingTime(exam)).toBe(3600); // 1 hour difference
        });

        it('should return explicit working time for TEST exam', () => {
            const exam = {
                examMode: ExamMode.TEST,
                startDate: dayjs().add(1, 'hour'),
                endDate: dayjs().add(3, 'hours'),
                workingTime: 1800, // Should use explicit working time
            } as Exam;
            expect(examWorkingTime(exam)).toBe(1800);
        });

        it('should return explicit working time for TEST_WITH_SIMULATION exam', () => {
            const exam = {
                examMode: ExamMode.TEST_WITH_SIMULATION,
                startDate: dayjs().add(1, 'hour'),
                endDate: dayjs().add(4, 'hours'),
                workingTime: 3600, // Should use explicit working time
            } as Exam;
            expect(examWorkingTime(exam)).toBe(3600);
        });
    });
});
