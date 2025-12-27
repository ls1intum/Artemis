/**
 * Vitest tests for CleanupServiceComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import dayjs from 'dayjs/esm';

import { CleanupServiceComponent } from 'app/core/admin/cleanup-service/cleanup-service.component';
import { CleanupOperation } from 'app/core/admin/cleanup-service/cleanup-operation.model';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/core/admin/cleanup-service/data-cleanup.service';

describe('CleanupServiceComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CleanupServiceComponent;
    let fixture: ComponentFixture<CleanupServiceComponent>;
    let cleanupService: DataCleanupService;

    beforeEach(async () => {
        const mockCleanupService = {
            getLastExecutions: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [CleanupServiceComponent],
            providers: [{ provide: DataCleanupService, useValue: mockCleanupService }],
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
});
