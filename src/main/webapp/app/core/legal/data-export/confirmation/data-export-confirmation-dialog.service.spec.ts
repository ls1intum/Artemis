import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { OutputEmitterRef, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { DataExportConfirmationDialogData } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.model';
import { DataExportConfirmationDialogService } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';

// Helper to create a mock OutputEmitterRef
function createMockOutputEmitterRef<T>(): OutputEmitterRef<T> {
    return {
        emit: vi.fn(),
        subscribe: vi.fn(),
        destroyed: false,
        listeners: new Set(),
        errorHandler: undefined,
    } as unknown as OutputEmitterRef<T>;
}

describe('DataExportConfirmationDialogService', () => {
    setupTestBed({ zoneless: true });

    let service: DataExportConfirmationDialogService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [DataExportConfirmationDialogService, { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(DataExportConfirmationDialogService);
        modalService = TestBed.inject(NgbModal);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should open confirmation dialog', () => {
        expect(service.modalRef).toBeUndefined();
        const data: DataExportConfirmationDialogData = {
            dialogError: new Observable<string>(),
            userLogin: 'userLogin',
            dataExportRequest: createMockOutputEmitterRef<void>(),
            dataExportRequestForAnotherUser: createMockOutputEmitterRef<string>(),
            adminDialog: false,
        };
        // Create mock componentInstance with signal-like properties
        const componentInstance = {
            expectedLogin: signal(''),
            adminDialog: signal(false),
            expectedLoginOfOtherUser: signal(''),
            dataExportRequest: undefined as unknown,
            dataExportRequestForAnotherUser: undefined as unknown,
            dialogError: undefined as unknown,
        };
        const result = new Promise((resolve) => resolve({}));
        const openModalStub = vi.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });
        service.openConfirmationDialog(data);
        expect(openModalStub).toHaveBeenCalledOnce();
        expect(openModalStub).toHaveBeenCalledWith(DataExportConfirmationDialogComponent, { size: 'lg', backdrop: 'static' });
    });
});
