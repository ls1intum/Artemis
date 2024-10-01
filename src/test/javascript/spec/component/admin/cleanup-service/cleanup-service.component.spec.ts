import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { ArtemisTestModule } from '../../../test.module';
import { CleanupServiceComponent } from 'app/admin/cleanup-service/cleanup-service.component';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { signal } from '@angular/core';

describe('CleanupServiceComponent', () => {
    let comp: CleanupServiceComponent;
    let fixture: ComponentFixture<CleanupServiceComponent>;
    let cleanupService: DataCleanupService;

    beforeEach(() => {
        const mockCleanupService = {
            getLastExecutions: jest.fn(),
            deleteOrphans: jest.fn(),
            deletePlagiarismComparisons: jest.fn(),
            deleteNonRatedResults: jest.fn(),
            deleteOldRatedResults: jest.fn(),
            deleteOldSubmissionVersions: jest.fn(),
            deleteOldFeedback: jest.fn(),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, CleanupServiceComponent],
            providers: [{ provide: DataCleanupService, useValue: mockCleanupService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CleanupServiceComponent);
                comp = fixture.componentInstance;
                cleanupService = TestBed.inject(DataCleanupService);
            });
    });

    it('should load last executions on init', () => {
        const executionRecord: CleanupServiceExecutionRecordDTO[] = [{ executionDate: dayjs(), jobType: 'deleteOrphans' }];
        const response = new HttpResponse<CleanupServiceExecutionRecordDTO[]>({
            body: executionRecord,
        });

        jest.spyOn(cleanupService, 'getLastExecutions').mockReturnValue(of(response));

        comp.ngOnInit();

        expect(cleanupService.getLastExecutions).toHaveBeenCalledOnce();
        expect(comp.cleanupOperations[0].lastExecuted).toEqual(dayjs(executionRecord[0].executionDate));
    });

    it('should execute a cleanup operation successfully', () => {
        const operation = comp.cleanupOperations[0];
        const response = new HttpResponse<CleanupServiceExecutionRecordDTO>({
            body: { executionDate: dayjs(), jobType: 'deleteOrphans' },
        });

        jest.spyOn(cleanupService, 'deleteOrphans').mockReturnValue(of(response));

        comp.executeCleanupOperation(operation);

        expect(cleanupService.deleteOrphans).toHaveBeenCalledOnce();
        expect(operation.lastExecuted).toEqual(dayjs(response.body!.executionDate));
    });

    it('should handle error when executing cleanup operation', () => {
        const operation = comp.cleanupOperations[0];
        const errorResponse = new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
            error: 'Some error message',
            url: 'https://artemis.ase.in.tum.de/api/admin/delete-orphans', // Adding a mock URL
        });

        jest.spyOn(cleanupService, 'deleteOrphans').mockReturnValue(throwError(() => errorResponse));

        let errorMessage: string | undefined;
        comp.dialogError.subscribe((error) => {
            errorMessage = error;
        });

        comp.executeCleanupOperation(operation);

        expect(cleanupService.deleteOrphans).toHaveBeenCalledOnce();
        expect(errorMessage).toBe('Http failure response for https://artemis.ase.in.tum.de/api/admin/delete-orphans: 500 Internal Server Error');
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

        expect(validOperation.datesValid()).toBeTrue();
        expect(invalidOperation.datesValid()).toBeFalse();
    });
});
