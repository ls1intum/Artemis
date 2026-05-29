import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CsvDecimalSeparator, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings, ExportModalComponent } from 'app/shared-ui/export/modal/export-modal.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';

describe('ExportModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExportModalComponent;
    let fixture: ComponentFixture<ExportModalComponent>;
    let dialogRef: DynamicDialogRef;
    let translateService: TranslateService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [NgbNavModule, FaIconComponent, ExportModalComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(DynamicDialogRef), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExportModalComponent, { remove: { imports: [TranslateDirective] }, add: { imports: [MockDirective(TranslateDirective)] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExportModalComponent);
                component = fixture.componentInstance;
                dialogRef = TestBed.inject(DynamicDialogRef);
                translateService = TestBed.inject(TranslateService);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should init with default options', () => {
        vi.spyOn(translateService, 'getCurrentLang').mockReturnValue('en');
        component.ngOnInit();
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.COMMA);
        expect(component.options.quoteCharacter).toBe(CsvQuoteStrings.QUOTES_DOUBLE);
        expect(component.options.quoteStrings).toBe(true);
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.PERIOD);
    });

    it('should init with german default options', () => {
        vi.spyOn(translateService, 'getCurrentLang').mockReturnValue('de');
        component.ngOnInit();
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.SEMICOLON);
        expect(component.options.quoteCharacter).toBe(CsvQuoteStrings.QUOTES_DOUBLE);
        expect(component.options.quoteStrings).toBe(true);
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.COMMA);
    });

    it('should set csv options', () => {
        component.setCsvFieldSeparator(CsvFieldSeparator.SPACE);
        component.setCsvQuoteString(CsvQuoteStrings.NONE);
        component.setCsvDecimalSeparator(CsvDecimalSeparator.PERIOD);
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.SPACE);
        expect(component.options.quoteCharacter).toBe(CsvQuoteStrings.NONE);
        expect(component.options.quoteStrings).toBe(false);
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.PERIOD);
    });

    it('should close (dismiss) the modal if close or cancel button are clicked', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const cancelButton = fixture.debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(closeSpy).toHaveBeenCalledWith();
    });

    it('should return an excel result on finish when excel export is active', () => {
        component.activeTab = 1;
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.onFinish();
        expect(closeSpy).toHaveBeenCalledWith({ type: 'excel' });
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
        expect(closeSpy).toHaveBeenCalledWith({ type: 'csv', options: testOptions });
    });
});
