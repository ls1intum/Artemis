import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ExportButtonComponent } from 'app/shared-ui/export/button/export-button.component';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { ExportDialogCloseResult } from 'app/shared-ui/export/modal/export-modal.component';
import { vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExportButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ExportButtonComponent>;
    let comp: ExportButtonComponent;
    let dialogService: DialogService;
    let dialogClose: Subject<ExportDialogCloseResult>;

    beforeEach(async () => {
        dialogClose = new Subject<ExportDialogCloseResult>();
        await TestBed.configureTestingModule({
            imports: [ExportButtonComponent, MockComponent(ButtonComponent)],
            providers: [
                { provide: DialogService, useValue: { open: vi.fn().mockReturnValue({ onClose: dialogClose } as unknown as DynamicDialogRef) } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExportButtonComponent);
        comp = fixture.componentInstance;
        dialogService = TestBed.inject(DialogService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const dialogOpenStub = vi.spyOn(dialogService, 'open');

        fixture.detectChanges();
        comp.openExportModal(new MouseEvent('click'));
        const csvExportButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(csvExportButton).not.toBeNull();
        expect(dialogOpenStub).toHaveBeenCalledOnce();
    });
});
