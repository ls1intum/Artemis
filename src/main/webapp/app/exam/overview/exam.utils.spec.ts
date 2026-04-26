import { isExamResultPublished } from 'app/exam/overview/exam.utils';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/exam/shared/entities/exam.model';
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
});
