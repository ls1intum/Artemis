/**
 * Vitest tests for CleanupServiceComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { CleanupServiceComponent } from 'app/admin/cleanup-service/cleanup-service.component';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CleanupServiceComponent', () => {
    let comp: CleanupServiceComponent;
    let fixture: ComponentFixture<CleanupServiceComponent>;
    let cleanupService: DataCleanupService;

    beforeEach(async () => {
        const mockCleanupService = {
            getLastExecutions: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [CleanupServiceComponent],
            providers: [
                { provide: DataCleanupService, useValue: mockCleanupService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(CleanupServiceComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CleanupServiceComponent);
        comp = fixture.componentInstance;
        cleanupService = TestBed.inject(DataCleanupService);
    });

    it('should load last executions on init', () => {
        const executionRecord: CleanupServiceExecutionRecordDTO[] = [{ executionDate: dayjs(), jobType: 'deleteOrphans' }];
        const response = new HttpResponse<CleanupServiceExecutionRecordDTO[]>({
            body: executionRecord,
        });

        vi.spyOn(cleanupService, 'getLastExecutions').mockReturnValue(of(response));

        comp.ngOnInit();

        expect(cleanupService.getLastExecutions).toHaveBeenCalledOnce();
        expect(comp.cleanupOperations()[0].lastExecuted).toEqual(dayjs(executionRecord[0].executionDate));
    });

    it('should match execution records by server job type, not array position', () => {
        // The server labels several jobs differently from the client operation names (client 'deleteOldRatedResults'
        // -> server 'deleteRatedResults', 'deleteOldSubmissionVersions' -> 'deleteSubmissionVersions'). A record must
        // be attributed to the operation whose serverJobTypeByName matches, never by index.
        const ratedDate = dayjs().subtract(1, 'day');
        const submissionDate = dayjs().subtract(2, 'days');
        const response = new HttpResponse<CleanupServiceExecutionRecordDTO[]>({
            body: [
                { executionDate: ratedDate, jobType: 'deleteRatedResults' },
                { executionDate: submissionDate, jobType: 'deleteSubmissionVersions' },
            ],
        });
        vi.spyOn(cleanupService, 'getLastExecutions').mockReturnValue(of(response));

        comp.ngOnInit();

        const operations = comp.cleanupOperations();
        expect(operations.find((operation) => operation.name === 'deleteOldRatedResults')?.lastExecuted).toEqual(dayjs(ratedDate));
        expect(operations.find((operation) => operation.name === 'deleteOldSubmissionVersions')?.lastExecuted).toEqual(dayjs(submissionDate));
        // The first operation ('deleteOrphans') must stay untouched even though records appeared first in the array.
        expect(operations.find((operation) => operation.name === 'deleteOrphans')?.lastExecuted).toBeUndefined();
    });

    it('should alert on a failed executions load', () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(cleanupService, 'getLastExecutions').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

        comp.ngOnInit();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should validate date ranges correctly', () => {
        const validOperation: CleanupOperation = {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };

        const invalidOperation: CleanupOperation = {
            name: 'deleteOrphans',
            deleteFrom: dayjs(),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
        };

        comp.validateDates(validOperation);
        comp.validateDates(invalidOperation);

        expect(validOperation.datesValid()).toBe(true);
        expect(invalidOperation.datesValid()).toBe(false);
    });

    it('should clear the model and invalidate the row when a date is cleared', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };

        // Clearing a field must drop the stale date so a deletion cannot run against a range no longer shown.
        comp.onDeleteFromChange(operation, undefined);

        expect(operation.deleteFrom).toBeUndefined();
        expect(operation.datesValid()).toBe(false);
    });

    it('should set a new from-date and revalidate the row', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            deleteFrom: undefined,
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(false),
        };

        const newFrom = dayjs().subtract(6, 'months');
        comp.onDeleteFromChange(operation, newFrom);

        expect(operation.deleteFrom?.toISOString()).toBe(newFrom.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('should clear the model and invalidate the row when the to-date is cleared', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };

        comp.onDeleteToChange(operation, undefined);

        expect(operation.deleteTo).toBeUndefined();
        expect(operation.datesValid()).toBe(false);
    });

    it('should set a new to-date and revalidate the row', () => {
        const operation: CleanupOperation = {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: undefined,
            lastExecuted: undefined,
            datesValid: signal(false),
        };

        const newTo = dayjs();
        comp.onDeleteToChange(operation, newTo);

        expect(operation.deleteTo?.toISOString()).toBe(newTo.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('should select the operation and show the modal when opened', () => {
        const operation = comp.cleanupOperations()[0];

        comp.openCleanupOperationModal(operation);

        expect(comp.selectedOperation()).toBe(operation);
        expect(comp.showCleanupModal()).toBe(true);
    });

    it('should expose the new data-privacy operations as age-based (no date range) and valid', () => {
        const operations = comp.cleanupOperations();
        const ageBasedNames = ['warnOldCoursesReset', 'resetOldCourses', 'deleteOldFeedback', 'deleteOldCourseSubmissionVersions', 'deleteNotEnrolledUsers'];

        for (const name of ageBasedNames) {
            const operation = operations.find((candidate) => candidate.name === name);
            expect(operation).toBeDefined();
            expect(operation!.ageBased).toBe(true);
            // age-based operations have no admin-picked date range and are always executable
            expect(operation!.deleteFrom).toBeUndefined();
            expect(operation!.deleteTo).toBeUndefined();
            expect(operation!.datesValid()).toBe(true);
        }
    });
});
