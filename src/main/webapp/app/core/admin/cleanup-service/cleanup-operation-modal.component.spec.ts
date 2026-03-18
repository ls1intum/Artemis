/**
 * Vitest tests for CleanupOperationModalComponent.
 * Tests the modal component for executing and monitoring cleanup operations.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentRef, signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { CleanupOperationModalComponent } from 'app/core/admin/cleanup-service/cleanup-operation-modal.component';
import { CleanupOperation, OperationName } from 'app/core/admin/cleanup-service/cleanup-operation.model';
import { CleanupCount, DataCleanupService, OrphanCleanupCountDTO, PlagiarismComparisonCleanupCountDTO } from 'app/core/admin/cleanup-service/data-cleanup.service';

/**
 * Helper to create a CleanupOperation with required properties
 */
function createOperation(name: OperationName): CleanupOperation {
    const operation = new CleanupOperation();
    operation.name = name;
    operation.deleteFrom = dayjs().subtract(1, 'year');
    operation.deleteTo = dayjs();
    operation.lastExecuted = undefined;
    operation.datesValid = signal(true);
    return operation;
}

describe('CleanupOperationModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CleanupOperationModalComponent;
    let componentRef: ComponentRef<CleanupOperationModalComponent>;
    let fixture: ComponentFixture<CleanupOperationModalComponent>;
    let dataCleanupService: DataCleanupService;

    const mockOrphanCounts: OrphanCleanupCountDTO = {
        totalCount: 100,
        orphanFeedback: 10,
        orphanLongFeedbackText: 5,
        orphanTextBlock: 15,
        orphanStudentScore: 20,
        orphanTeamScore: 10,
        orphanFeedbackForOrphanResults: 5,
        orphanLongFeedbackTextForOrphanResults: 3,
        orphanTextBlockForOrphanResults: 7,
        orphanRating: 10,
        orphanResultsWithoutParticipation: 15,
    };

    const mockPlagiarismCounts: PlagiarismComparisonCleanupCountDTO = {
        totalCount: 50,
        plagiarismComparison: 10,
        plagiarismElements: 20,
        plagiarismSubmissions: 10,
        plagiarismMatches: 10,
    };

    const mockNonRatedCounts: CleanupCount = { totalCount: 25 };
    const mockOldRatedCounts: CleanupCount = { totalCount: 30 };
    const mockSubmissionVersionCounts: CleanupCount = { totalCount: 40 };

    const deleteOrphansOperation = createOperation('deleteOrphans');
    const deletePlagiarismOperation = createOperation('deletePlagiarismComparisons');
    const deleteNonRatedOperation = createOperation('deleteNonRatedResults');
    const deleteOldRatedOperation = createOperation('deleteOldRatedResults');
    const deleteSubmissionVersionsOperation = createOperation('deleteOldSubmissionVersions');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CleanupOperationModalComponent],
            providers: [
                {
                    provide: DataCleanupService,
                    useValue: {
                        countOrphans: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockOrphanCounts }))),
                        countPlagiarismComparisons: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockPlagiarismCounts }))),
                        countNonRatedResults: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockNonRatedCounts }))),
                        countOldRatedResults: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockOldRatedCounts }))),
                        countOldSubmissionVersions: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockSubmissionVersionCounts }))),
                        deleteOrphans: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deletePlagiarismComparisons: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteNonRatedResults: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteOldRatedResults: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteOldSubmissionVersions: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                    },
                },
            ],
        })
            .overrideTemplate(CleanupOperationModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CleanupOperationModalComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        dataCleanupService = TestBed.inject(DataCleanupService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create component', () => {
        componentRef.setInput('operation', deleteOrphansOperation);
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should have counts initialized with zero totalCount', () => {
        componentRef.setInput('operation', deleteOrphansOperation);
        expect(component.counts().totalCount).toBe(0);
    });

    it('should have operationExecuted initialized to false', () => {
        componentRef.setInput('operation', deleteOrphansOperation);
        expect(component.operationExecuted()).toBe(false);
    });

    describe('loading counts on visible', () => {
        it('should fetch orphan counts for deleteOrphans operation when visible', () => {
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countOrphans).toHaveBeenCalled();
            expect(component.counts()).toEqual(mockOrphanCounts);
        });

        it('should fetch plagiarism counts for deletePlagiarismComparisons operation when visible', () => {
            componentRef.setInput('operation', deletePlagiarismOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countPlagiarismComparisons).toHaveBeenCalledWith(deletePlagiarismOperation.deleteFrom, deletePlagiarismOperation.deleteTo);
            expect(component.counts()).toEqual(mockPlagiarismCounts);
        });

        it('should fetch non-rated counts for deleteNonRatedResults operation when visible', () => {
            componentRef.setInput('operation', deleteNonRatedOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countNonRatedResults).toHaveBeenCalledWith(deleteNonRatedOperation.deleteFrom, deleteNonRatedOperation.deleteTo);
            expect(component.counts()).toEqual(mockNonRatedCounts);
        });

        it('should fetch old rated counts for deleteOldRatedResults operation when visible', () => {
            componentRef.setInput('operation', deleteOldRatedOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countOldRatedResults).toHaveBeenCalledWith(deleteOldRatedOperation.deleteFrom, deleteOldRatedOperation.deleteTo);
            expect(component.counts()).toEqual(mockOldRatedCounts);
        });

        it('should fetch submission version counts for deleteOldSubmissionVersions operation when visible', () => {
            componentRef.setInput('operation', deleteSubmissionVersionsOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countOldSubmissionVersions).toHaveBeenCalledWith(deleteSubmissionVersionsOperation.deleteFrom, deleteSubmissionVersionsOperation.deleteTo);
            expect(component.counts()).toEqual(mockSubmissionVersionCounts);
        });

        it('should emit error to dialogError when fetching counts fails', () => {
            vi.spyOn(dataCleanupService, 'countOrphans').mockReturnValue(throwError(() => new Error('Network error')));
            componentRef.setInput('operation', deleteOrphansOperation);

            let emittedError: string | undefined;
            component.dialogError.subscribe((error) => (emittedError = error));

            component.visible.set(true);
            fixture.detectChanges();

            expect(emittedError).toBe('An error occurred while fetching updated counts.');
        });
    });

    describe('close', () => {
        it('should set visible to false', () => {
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.close();

            expect(component.visible()).toBe(false);
        });
    });

    describe('executeCleanupOperation', () => {
        it('should execute deleteOrphans operation', () => {
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.deleteOrphans).toHaveBeenCalled();
            expect(component.operationExecuted()).toBe(true);
        });

        it('should execute deletePlagiarismComparisons operation', () => {
            componentRef.setInput('operation', deletePlagiarismOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.deletePlagiarismComparisons).toHaveBeenCalledWith(deletePlagiarismOperation.deleteFrom, deletePlagiarismOperation.deleteTo);
            expect(component.operationExecuted()).toBe(true);
        });

        it('should execute deleteNonRatedResults operation', () => {
            componentRef.setInput('operation', deleteNonRatedOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.deleteNonRatedResults).toHaveBeenCalledWith(deleteNonRatedOperation.deleteFrom, deleteNonRatedOperation.deleteTo);
            expect(component.operationExecuted()).toBe(true);
        });

        it('should execute deleteOldRatedResults operation', () => {
            componentRef.setInput('operation', deleteOldRatedOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.deleteOldRatedResults).toHaveBeenCalledWith(deleteOldRatedOperation.deleteFrom, deleteOldRatedOperation.deleteTo);
            expect(component.operationExecuted()).toBe(true);
        });

        it('should execute deleteOldSubmissionVersions operation', () => {
            componentRef.setInput('operation', deleteSubmissionVersionsOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.deleteOldSubmissionVersions).toHaveBeenCalledWith(deleteSubmissionVersionsOperation.deleteFrom, deleteSubmissionVersionsOperation.deleteTo);
            expect(component.operationExecuted()).toBe(true);
        });

        it('should update counts after successful operation execution', () => {
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            // countOrphans called twice: once on visible, once after execution
            expect(dataCleanupService.countOrphans).toHaveBeenCalledTimes(2);
        });

        it('should emit HttpErrorResponse message to dialogError on operation failure', () => {
            const httpError = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: { message: 'Delete failed' } });
            vi.spyOn(dataCleanupService, 'deleteOrphans').mockReturnValue(throwError(() => httpError));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            let emittedError: string | undefined;
            component.dialogError.subscribe((error) => (emittedError = error));

            component.executeCleanupOperation();

            expect(emittedError).toBe(httpError.message);
            expect(component.operationExecuted()).toBe(false);
        });

        it('should emit generic error message for non-HttpErrorResponse failures', () => {
            vi.spyOn(dataCleanupService, 'deleteOrphans').mockReturnValue(throwError(() => new Error('Some error')));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            let emittedError: string | undefined;
            component.dialogError.subscribe((error) => (emittedError = error));

            component.executeCleanupOperation();

            expect(emittedError).toBe('An unexpected error occurred.');
        });
    });

    describe('computed properties', () => {
        it('should return cleanup keys from counts object', () => {
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            const keys = component.cleanupKeys();
            expect(keys).toContain('totalCount');
            expect(keys).toContain('orphanFeedback');
        });

        it('should return hasEntriesToDelete true when there are entries to delete', () => {
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(component.hasEntriesToDelete()).toBe(true);
        });

        it('should return hasEntriesToDelete false when all counts are zero', () => {
            const zeroCounts: OrphanCleanupCountDTO = {
                totalCount: 0,
                orphanFeedback: 0,
                orphanLongFeedbackText: 0,
                orphanTextBlock: 0,
                orphanStudentScore: 0,
                orphanTeamScore: 0,
                orphanFeedbackForOrphanResults: 0,
                orphanLongFeedbackTextForOrphanResults: 0,
                orphanTextBlockForOrphanResults: 0,
                orphanRating: 0,
                orphanResultsWithoutParticipation: 0,
            };
            vi.spyOn(dataCleanupService, 'countOrphans').mockReturnValue(of(new HttpResponse({ body: zeroCounts })));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(component.hasEntriesToDelete()).toBe(false);
        });
    });
});
