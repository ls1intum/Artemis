import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DataExportConfirmationDialogService } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';

describe('DataExportConfirmationDialogService', () => {
    setupTestBed({ zoneless: true });

    let service: DataExportConfirmationDialogService;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [DataExportConfirmationDialogService, { provide: TranslateService, useClass: MockTranslateService }, MockProvider(AlertService)],
        });
        service = TestBed.inject(DataExportConfirmationDialogService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call alertService.closeAll when openConfirmationDialog is called', () => {
        const closeAllSpy = vi.spyOn(alertService, 'closeAll');
        service.openConfirmationDialog();
        expect(closeAllSpy).toHaveBeenCalledOnce();
    });
});
