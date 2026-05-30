import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CsvDecimalSeparator, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings, ExportModalComponent } from 'app/shared-ui/export/modal/export-modal.component';
import { By } from '@angular/platform-browser';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExportModalComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExportModalComponent;
    let fixture: ComponentFixture<ExportModalComponent>;
    let dialogRef: DynamicDialogRef;
    let translateService: TranslateService;

    beforeEach(async () => {
        const mockDialogRef = {
            close: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ExportModalComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogRef, useValue: mockDialogRef },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExportModalComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
        translateService = TestBed.inject(TranslateService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should init with default options', () => {
        vi.spyOn(translateService, 'getCurrentLang').mockReturnValue('en');
        component.ngOnInit();
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.COMMA);
        expect(component.options.quoteCharacter).toBe(CsvQuoteStrings.QUOTES_DOUBLE);
        expect(component.options.quoteStrings).toBeTruthy();
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.PERIOD);
    });

    it('should init with german default options', () => {
        vi.spyOn(translateService, 'getCurrentLang').mockReturnValue('de');
        component.ngOnInit();
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.SEMICOLON);
        expect(component.options.quoteCharacter).toBe(CsvQuoteStrings.QUOTES_DOUBLE);
        expect(component.options.quoteStrings).toBeTruthy();
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.COMMA);
    });

    it('should set csv options', () => {
        component.setCsvFieldSeparator(CsvFieldSeparator.SPACE);
        component.setCsvQuoteString(CsvQuoteStrings.NONE);
        component.setCsvDecimalSeparator(CsvDecimalSeparator.PERIOD);
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.SPACE);
        expect(component.options.quoteCharacter).toBe(CsvQuoteStrings.NONE);
        expect(component.options.quoteStrings).toBeFalsy();
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.PERIOD);
    });

    it('should dismiss modal if close or cancel button are clicked', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const closeButton = fixture.debugElement.query(By.css('button.btn-close'));
        expect(closeButton).not.toBeNull();
        closeButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith({ cancelled: true });

        const cancelButton = fixture.debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledTimes(2);
        expect(closeSpy).toHaveBeenLastCalledWith({ cancelled: true });
    });

    it('should return empty on finish when excel export is active', () => {
        component.activeTab = 1;
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.onFinish();
        expect(closeSpy).toHaveBeenCalledWith();
    });

    it('should return the csv export options on finish when csv export is active', () => {
        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteStrings: true,
            quoteCharacter: CsvQuoteStrings.QUOTES_SINGLE,
            decimalSeparator: CsvDecimalSeparator.COMMA,
        };
        component.options = testOptions;
        component.activeTab = 2;
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.onFinish();
        expect(closeSpy).toHaveBeenCalledWith(testOptions);
    });
});
