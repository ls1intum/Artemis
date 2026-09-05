/**
 * Vitest tests for CleanupOperationModalComponent.
 * Tests the modal component for executing and monitoring cleanup operations.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef, signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { CleanupOperationModalComponent } from 'app/admin/cleanup-service/cleanup-operation-modal.component';
import { CleanupOperation, OperationName } from 'app/admin/cleanup-service/cleanup-operation.model';
import {
    CleanupCount,
    CleanupServiceExecutionRecordDTO,
    DataCleanupService,
    OrphanCleanupCountDTO,
    PlagiarismComparisonCleanupCountDTO,
} from 'app/admin/cleanup-service/data-cleanup.service';

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
        orphanFeedbackMessage: 3,
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

    function createAgeBasedOperation(name: OperationName): CleanupOperation {
        const operation = new CleanupOperation();
        operation.name = name;
        operation.deleteFrom = undefined;
        operation.deleteTo = undefined;
        operation.lastExecuted = undefined;
        operation.datesValid = signal(true);
        operation.ageBased = true;
        return operation;
    }

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
                        countOldCoursesResetWarning: vi.fn().mockReturnValue(of(new HttpResponse({ body: { courses: 3 } }))),
                        countOldCoursesReset: vi.fn().mockReturnValue(of(new HttpResponse({ body: { courses: 2 } }))),
                        countOldFeedback: vi.fn().mockReturnValue(of(new HttpResponse({ body: { longFeedbackText: 1, textBlock: 2, feedback: 3 } }))),
                        countOldCourseSubmissionVersions: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockSubmissionVersionCounts }))),
                        countNotEnrolledUsers: vi.fn().mockReturnValue(of(new HttpResponse({ body: { users: 4 } }))),
                        countPlagiarismCases: vi.fn().mockReturnValue(of(new HttpResponse({ body: { plagiarismCases: 3 } }))),
                        deleteOrphans: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deletePlagiarismComparisons: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteNonRatedResults: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteOldRatedResults: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteOldSubmissionVersions: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        warnOldCoursesReset: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        resetOldCourses: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteOldFeedback: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteOldCourseSubmissionVersions: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deleteNotEnrolledUsers: vi.fn().mockReturnValue(of(new HttpResponse({}))),
                        deletePlagiarismCases: vi.fn().mockReturnValue(of(new HttpResponse({}))),
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

        it('should fetch warn counts for the age-based warnOldCoursesReset operation without a date range', () => {
            componentRef.setInput('operation', createAgeBasedOperation('warnOldCoursesReset'));
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countOldCoursesResetWarning).toHaveBeenCalled();
            expect(component.counts()).toEqual({ courses: 3 });
        });

        it('should fetch user counts for the age-based deleteNotEnrolledUsers operation without a date range', () => {
            componentRef.setInput('operation', createAgeBasedOperation('deleteNotEnrolledUsers'));
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countNotEnrolledUsers).toHaveBeenCalled();
            expect(component.counts()).toEqual({ users: 4 });
        });

        it('should fetch plagiarism-case counts for the age-based deletePlagiarismCases operation without a date range', () => {
            componentRef.setInput('operation', createAgeBasedOperation('deletePlagiarismCases'));
            component.visible.set(true);
            fixture.detectChanges();

            expect(dataCleanupService.countPlagiarismCases).toHaveBeenCalled();
            expect(component.counts()).toEqual({ plagiarismCases: 3 });
        });

        it('should set dialogError when fetching counts fails', () => {
            vi.spyOn(dataCleanupService, 'countOrphans').mockReturnValue(throwError(() => new Error('Network error')));
            componentRef.setInput('operation', deleteOrphansOperation);

            component.visible.set(true);
            fixture.detectChanges();

            expect(component.dialogError()).toBe('An error occurred while fetching updated counts.');
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

    describe('reopening', () => {
        it('should reset operationExecuted and counts when reopened', () => {
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            // Run the operation so both result flags become "dirty".
            component.executeCleanupOperation();
            expect(component.operationExecuted()).toBe(true);
            expect(component.counts()).toEqual(mockOrphanCounts);

            component.close();
            fixture.detectChanges();

            // Reopening must clear the previous run's result state. Fail the reopen count-fetch so the reset
            // value (totalCount 0) stays observable instead of being immediately overwritten by fresh counts.
            vi.spyOn(dataCleanupService, 'countOrphans').mockReturnValue(throwError(() => new Error('Network error')));
            component.visible.set(true);
            fixture.detectChanges();

            expect(component.operationExecuted()).toBe(false);
            expect(component.counts()).toEqual({ totalCount: 0 });
        });

        it('should clear the previous error when reopened', () => {
            vi.spyOn(dataCleanupService, 'countOrphans')
                .mockReturnValueOnce(throwError(() => new Error('Network error')))
                .mockReturnValue(of(new HttpResponse({ body: mockOrphanCounts })));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();
            expect(component.dialogError()).toBe('An error occurred while fetching updated counts.');

            component.close();
            fixture.detectChanges();
            component.visible.set(true);
            fixture.detectChanges();

            expect(component.dialogError()).toBeUndefined();
        });

        it('should ignore a stale count response from a previously opened operation', () => {
            // The modal instance persists across opens. Open A with a count request that never resolves yet.
            const orphanCounts = new Subject<HttpResponse<OrphanCleanupCountDTO>>();
            vi.spyOn(dataCleanupService, 'countOrphans').mockReturnValue(orphanCounts);

            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            // Close (cancels A's pending request), then reopen for B, whose counts resolve synchronously.
            component.close();
            fixture.detectChanges();
            componentRef.setInput('operation', deleteNonRatedOperation);
            component.visible.set(true);
            fixture.detectChanges();
            expect(component.counts()).toEqual(mockNonRatedCounts);

            // A's late response must not overwrite B's counts.
            orphanCounts.next(new HttpResponse({ body: mockOrphanCounts }));
            expect(component.counts()).toEqual(mockNonRatedCounts);
        });
    });

    describe('executeCleanupOperation', () => {
        it('should ignore duplicate execution attempts while a cleanup request is in flight', () => {
            const execution = new Subject<HttpResponse<CleanupServiceExecutionRecordDTO>>();
            vi.spyOn(dataCleanupService, 'deleteOrphans').mockReturnValue(execution);
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();
            component.executeCleanupOperation();

            expect(dataCleanupService.deleteOrphans).toHaveBeenCalledOnce();
            expect(component.operationExecuting()).toBe(true);

            execution.next(new HttpResponse({ body: { executionDate: dayjs(), jobType: 'deleteOrphans' } }));
            execution.complete();
            expect(component.operationExecuting()).toBe(false);
            expect(component.operationExecuted()).toBe(true);
        });

        it('should ignore a stale cleanup completion after closing and reopening for another operation', () => {
            const execution = new Subject<HttpResponse<CleanupServiceExecutionRecordDTO>>();
            vi.spyOn(dataCleanupService, 'deleteOrphans').mockReturnValue(execution);
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();
            component.executeCleanupOperation();

            component.close();
            fixture.detectChanges();
            componentRef.setInput('operation', deleteNonRatedOperation);
            component.visible.set(true);
            fixture.detectChanges();

            // Closing cannot cancel deletion after the server has started it. Keep the execution guard across the
            // reopen so the newly displayed operation cannot submit a second destructive request concurrently.
            expect(component.operationExecuting()).toBe(true);
            component.executeCleanupOperation();
            expect(dataCleanupService.deleteNonRatedResults).not.toHaveBeenCalled();

            execution.next(new HttpResponse({ body: { executionDate: dayjs(), jobType: 'deleteOrphans' } }));
            execution.complete();

            expect(component.operationExecuting()).toBe(false);
            expect(component.operationExecuted()).toBe(false);
            expect(component.counts()).toEqual(mockNonRatedCounts);
        });

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

        it('should execute the age-based resetOldCourses operation without a date range', () => {
            componentRef.setInput('operation', createAgeBasedOperation('resetOldCourses'));
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.resetOldCourses).toHaveBeenCalled();
            expect(component.operationExecuted()).toBe(true);
        });

        it('should execute the age-based deleteNotEnrolledUsers operation without a date range', () => {
            componentRef.setInput('operation', createAgeBasedOperation('deleteNotEnrolledUsers'));
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.deleteNotEnrolledUsers).toHaveBeenCalled();
            expect(component.operationExecuted()).toBe(true);
        });

        it('should execute the age-based deletePlagiarismCases operation without a date range', () => {
            componentRef.setInput('operation', createAgeBasedOperation('deletePlagiarismCases'));
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(dataCleanupService.deletePlagiarismCases).toHaveBeenCalled();
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

        it('should set the HttpErrorResponse message on dialogError when an operation fails', () => {
            const httpError = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: { message: 'Delete failed' } });
            vi.spyOn(dataCleanupService, 'deleteOrphans').mockReturnValue(throwError(() => httpError));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(component.dialogError()).toBe(httpError.message);
            expect(component.operationExecuted()).toBe(false);
            // The guard must be released on failure (via finalize) so the operation can be retried.
            expect(component.operationExecuting()).toBe(false);
        });

        it('should emit generic error message for non-HttpErrorResponse failures', () => {
            vi.spyOn(dataCleanupService, 'deleteOrphans').mockReturnValue(throwError(() => new Error('Some error')));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            component.executeCleanupOperation();

            expect(component.dialogError()).toBe('An unexpected error occurred.');
            expect(component.operationExecuting()).toBe(false);
        });

        it('should clear a previous error when re-executing in place after a failure', () => {
            const httpError = new HttpErrorResponse({ status: 500, error: { message: 'Delete failed' } });
            vi.spyOn(dataCleanupService, 'deleteOrphans')
                .mockReturnValueOnce(throwError(() => httpError))
                .mockReturnValueOnce(of(new HttpResponse({ body: { executionDate: dayjs(), jobType: 'deleteOrphans' } })));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            // First attempt fails and shows an error banner.
            component.executeCleanupOperation();
            expect(component.dialogError()).toBe(httpError.message);

            // A successful in-place retry must clear the stale error rather than show it beside the success state.
            component.executeCleanupOperation();
            expect(component.dialogError()).toBeUndefined();
            expect(component.operationExecuted()).toBe(true);
        });

        it('should ignore a cleanup completion delivered after the modal was closed', () => {
            const execution = new Subject<HttpResponse<CleanupServiceExecutionRecordDTO>>();
            vi.spyOn(dataCleanupService, 'deleteOrphans').mockReturnValue(execution);
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();
            component.executeCleanupOperation();

            // Close without reopening; the DELETE is still running server-side.
            component.close();
            fixture.detectChanges();

            // The completion arrives while the modal is closed (same operation), so only the !visible() half of the
            // stale-completion guard can trip: it must not flip operationExecuted, and the guard must be released.
            execution.next(new HttpResponse({ body: { executionDate: dayjs(), jobType: 'deleteOrphans' } }));
            execution.complete();

            expect(component.operationExecuted()).toBe(false);
            expect(component.operationExecuting()).toBe(false);
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
                orphanFeedbackMessage: 0,
            };
            vi.spyOn(dataCleanupService, 'countOrphans').mockReturnValue(of(new HttpResponse({ body: zeroCounts })));
            componentRef.setInput('operation', deleteOrphansOperation);
            component.visible.set(true);
            fixture.detectChanges();

            expect(component.hasEntriesToDelete()).toBe(false);
        });
    });
});
