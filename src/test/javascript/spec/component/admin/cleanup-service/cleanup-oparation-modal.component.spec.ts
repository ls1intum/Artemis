import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CleanupOperationModalComponent } from 'app/admin/cleanup-service/cleanup-operation-modal.component';
import { CleanupCount, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { signal } from '@angular/core';
import { MockDirective } from 'ng-mocks';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CleanupOperationModalComponent', () => {
    let comp: CleanupOperationModalComponent;
    let fixture: ComponentFixture<CleanupOperationModalComponent>;
    let cleanupService: DataCleanupService;
    let activeModal: NgbActiveModal;

    const mockCleanupService = {
        deleteOrphans: jest.fn(),
        deletePlagiarismComparisons: jest.fn(),
        deleteNonRatedResults: jest.fn(),
        deleteOldRatedResults: jest.fn(),
        countOrphans: jest.fn(),
        countPlagiarismComparisons: jest.fn(),
        countNonRatedResults: jest.fn(),
        countOldRatedResults: jest.fn(),
    };

    const mockActiveModal = {
        close: jest.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CleanupOperationModalComponent, ArtemisSharedModule],
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
        const mockCounts: CleanupCount = { totalCount: 10 };
        jest.spyOn(cleanupService, 'countOrphans').mockReturnValue(of(new HttpResponse({ body: mockCounts })));

        const operation = {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };
        fixture.componentRef.setInput('operation', operation);
        fixture.detectChanges();

        expect(cleanupService.countOrphans).toHaveBeenCalledOnce();
        expect(comp.counts).toEqual(mockCounts);
    });

    it('should execute cleanup operation successfully', () => {
        const mockResponse: HttpResponse<any> = new HttpResponse({
            body: { executionDate: dayjs(), jobType: 'deleteOrphans' },
        });

        const mockCounts: CleanupCount = { totalCount: 5 };
        jest.spyOn(cleanupService, 'deleteOrphans').mockReturnValue(of(mockResponse));
        jest.spyOn(cleanupService, 'countOrphans').mockReturnValue(of(new HttpResponse({ body: mockCounts })));

        const operation = {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };
        fixture.componentRef.setInput('operation', operation);

        comp.executeCleanupOperation();

        expect(cleanupService.deleteOrphans).toHaveBeenCalledOnce();
        expect(cleanupService.countOrphans).toHaveBeenCalledOnce();
        expect(comp.operationExecuted).toBeTrue();
        expect(comp.counts).toEqual(mockCounts);
    });

    it('should handle error during cleanup operation', () => {
        const errorResponse = new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
            error: 'Some error message',
            url: 'https://artemis.ase.in.tum.de/api/admin/orphans', // Adding a mock URL
        });

        jest.spyOn(cleanupService, 'deleteOrphans').mockReturnValue(throwError(() => errorResponse));

        let errorMessage: string | undefined;
        comp.dialogError.subscribe((error) => {
            errorMessage = error;
        });

        const operation = {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: undefined,
            datesValid: signal(true),
        };
        fixture.componentRef.setInput('operation', operation);

        comp.executeCleanupOperation();

        expect(cleanupService.deleteOrphans).toHaveBeenCalledOnce();
        expect(errorMessage).toBe('Http failure response for https://artemis.ase.in.tum.de/api/admin/orphans: 500 Internal Server Error');
    });

    it('should close the modal', () => {
        comp.close();

        expect(activeModal.close).toHaveBeenCalledOnce();
    });

    it('should compute hasEntriesToDelete correctly', () => {
        comp.counts = { totalCount: 0 };

        expect(comp.hasEntriesToDelete).toBeFalse();

        comp.counts = { totalCount: 5 };

        expect(comp.hasEntriesToDelete).toBeTrue();
    });
});
