import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ExportButtonComponent } from 'app/shared-ui/export/button/export-button.component';
import { CsvDecimalSeparator, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings, ExportModalResult } from 'app/shared-ui/export/modal/export-modal.component';

describe('ExportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExportButtonComponent>;
    let comp: ExportButtonComponent;
    let dialogService: DialogService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExportButtonComponent, MockComponent(ButtonComponent)],
            providers: [DialogService, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExportButtonComponent);
                comp = fixture.componentInstance;
                dialogService = TestBed.inject(DialogService);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and open the export dialog', () => {
        const onClose = new Subject<ExportModalResult | undefined>();
        const dialogServiceOpenStub = vi.spyOn(dialogService, 'open').mockReturnValue({ onClose } as unknown as DynamicDialogRef);

        comp.openExportModal(new MouseEvent('click'));

        const csvExportButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(csvExportButton).not.toBeNull();
        expect(dialogServiceOpenStub).toHaveBeenCalledOnce();
        // Assert the dialog config reproduces the original NgbModal contract: closable (X button), Esc dismissal,
        // static backdrop (dismissableMask false), and a non-draggable/non-resizable dialog.
        const config = dialogServiceOpenStub.mock.calls[0][1];
        expect(config).toMatchObject({
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            resizable: false,
        });
    });

    it('should emit chosen csv options when the dialog returns a csv result', () => {
        const onClose = new Subject<ExportModalResult | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose } as unknown as DynamicDialogRef);
        const emitSpy = vi.spyOn(comp.onExport, 'emit');
        const options: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.COMMA,
            quoteStrings: true,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        };

        comp.openExportModal(new MouseEvent('click'));
        onClose.next({ type: 'csv', options });

        expect(emitSpy).toHaveBeenCalledWith(options);
    });

    it('should emit undefined when the dialog returns an excel result', () => {
        const onClose = new Subject<ExportModalResult | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose } as unknown as DynamicDialogRef);
        const emitSpy = vi.spyOn(comp.onExport, 'emit');

        comp.openExportModal(new MouseEvent('click'));
        onClose.next({ type: 'excel' });

        expect(emitSpy).toHaveBeenCalledWith(undefined);
    });

    it('should not emit when the dialog is dismissed', () => {
        const onClose = new Subject<ExportModalResult | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose } as unknown as DynamicDialogRef);
        const emitSpy = vi.spyOn(comp.onExport, 'emit');

        comp.openExportModal(new MouseEvent('click'));
        onClose.next(undefined);

        expect(emitSpy).not.toHaveBeenCalled();
    });
});
