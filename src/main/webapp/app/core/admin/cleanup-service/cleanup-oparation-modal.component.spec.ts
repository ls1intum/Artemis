import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CleanupOperationModalComponent } from 'app/core/admin/cleanup-service/cleanup-operation-modal.component';
import { DataCleanupService, PlagiarismComparisonCleanupCountDTO } from 'app/core/admin/cleanup-service/data-cleanup.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { signal } from '@angular/core';
import { MockDirective } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CleanupOperationModalComponent', () => {
    let comp: CleanupOperationModalComponent;
    let fixture: ComponentFixture<CleanupOperationModalComponent>;
    let cleanupService: DataCleanupService;
    let activeModal: NgbActiveModal;

    const mockCleanupService = {
        deletePlagiarismComparisons: jest.fn(),
        countPlagiarismComparisons: jest.fn(),
    };

    const mockActiveModal = {
        close: jest.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CleanupOperationModalComponent],
            declarations: [MockDirective(TranslateDirective)],
            providers: [
                { provide: DataCleanupService, useValue: mockCleanupService },
                { provide: NgbActiveModal, useValue: mockActiveModal },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CleanupOperationModalComponent);
                comp = fixture.componentInstance;
                cleanupService = TestBed.inject(DataCleanupService);
                activeModal = TestBed.inject(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize and fetch counts on init', () => {
        const mockCounts: PlagiarismComparisonCleanupCountDTO = {
            totalCount: 10,
            plagiarismComparison: 5,
            plagiarismElements: 3,
            plagiarismSubmissions: 1,
            plagiarismMatches: 1,
        };
        jest.spyOn(cleanupService, 'countPlagiarismComparisons').mockReturnValue(of(new HttpResponse({ body: mockCounts })));

        const operation = {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };
        fixture.componentRef.setInput('operation', operation);
        fixture.detectChanges();

        expect(cleanupService.countPlagiarismComparisons).toHaveBeenCalledOnce();
        expect(comp.counts).toEqual(mockCounts);
    });

    it('should execute cleanup operation successfully', () => {
        const mockResponse: HttpResponse<any> = new HttpResponse({
            body: { executionDate: dayjs(), jobType: 'deletePlagiarismComparisons' },
        });

        const mockCounts: PlagiarismComparisonCleanupCountDTO = {
            totalCount: 5,
            plagiarismComparison: 2,
            plagiarismElements: 1,
            plagiarismSubmissions: 1,
            plagiarismMatches: 1,
        };
        jest.spyOn(cleanupService, 'deletePlagiarismComparisons').mockReturnValue(of(mockResponse));
        jest.spyOn(cleanupService, 'countPlagiarismComparisons').mockReturnValue(of(new HttpResponse({ body: mockCounts })));

        const operation = {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };
        fixture.componentRef.setInput('operation', operation);

        comp.executeCleanupOperation();

        expect(cleanupService.deletePlagiarismComparisons).toHaveBeenCalledOnce();
        expect(cleanupService.countPlagiarismComparisons).toHaveBeenCalledOnce();
        expect(comp.operationExecuted).toBeTrue();
        expect(comp.counts).toEqual(mockCounts);
    });

    it('should handle error during cleanup operation', () => {
        const errorResponse = new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
            error: 'Some error message',
            url: 'https://artemis.ase.in.tum.de/api/plagiarism/admin/plagiarism-comparisons', // Mock URL
        });

        jest.spyOn(cleanupService, 'deletePlagiarismComparisons').mockReturnValue(throwError(() => errorResponse));

        let errorMessage: string | undefined;
        comp.dialogError.subscribe((error) => {
            errorMessage = error;
        });

        const operation = {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };
        fixture.componentRef.setInput('operation', operation);

        comp.executeCleanupOperation();

        expect(cleanupService.deletePlagiarismComparisons).toHaveBeenCalledOnce();
        expect(errorMessage).toBe('Http failure response for https://artemis.ase.in.tum.de/api/plagiarism/admin/plagiarism-comparisons: 500 Internal Server Error');
    });

    it('should close the modal', () => {
        comp.close();

        expect(activeModal.close).toHaveBeenCalledOnce();
    });

    it('should compute hasEntriesToDelete correctly', () => {
        let mockCounts: PlagiarismComparisonCleanupCountDTO = { totalCount: 0, plagiarismComparison: 0, plagiarismElements: 0, plagiarismSubmissions: 0, plagiarismMatches: 0 };
        comp.counts = mockCounts;

        expect(comp.hasEntriesToDelete).toBeFalse();

        mockCounts = { totalCount: 5, plagiarismComparison: 2, plagiarismElements: 1, plagiarismSubmissions: 1, plagiarismMatches: 1 };
        comp.counts = mockCounts;

        expect(comp.hasEntriesToDelete).toBeTrue();
    });
});
