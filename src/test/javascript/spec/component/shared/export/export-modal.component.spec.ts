import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal, NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { CsvDecimalSeparator, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings, ExportModalComponent } from 'app/shared/export/export-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';

describe('ExportModalComponent', () => {
    let component: ExportModalComponent;
    let fixture: ComponentFixture<ExportModalComponent>;
    let ngbActiveModal: NgbActiveModal;
    let translateService: TranslateService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [NgbNavModule],
            declarations: [ExportModalComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbActiveModal), MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExportModalComponent);
                component = fixture.componentInstance;
                ngbActiveModal = TestBed.inject(NgbActiveModal);
                translateService = TestBed.inject(TranslateService);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should init with default options', () => {
        jest.spyOn(translateService, 'currentLang', 'get').mockReturnValue('en');
        component.ngOnInit();
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.COMMA);
        expect(component.options.quoteStrings).toBe(CsvQuoteStrings.QUOTES_DOUBLE);
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.PERIOD);
    });

    it('should init with german default options', () => {
        jest.spyOn(translateService, 'currentLang', 'get').mockReturnValue('de');
        component.ngOnInit();
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.SEMICOLON);
        expect(component.options.quoteStrings).toBe(CsvQuoteStrings.QUOTES_DOUBLE);
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.COMMA);
    });

    it('should set csv options', () => {
        component.setCsvFieldSeparator(CsvFieldSeparator.SPACE);
        component.setCsvQuoteString(CsvQuoteStrings.NONE);
        component.setCsvDecimalSeparator(CsvDecimalSeparator.PERIOD);
        expect(component.options.fieldSeparator).toBe(CsvFieldSeparator.SPACE);
        expect(component.options.quoteStrings).toBe(CsvQuoteStrings.NONE);
        expect(component.options.decimalSeparator).toBe(CsvDecimalSeparator.PERIOD);
    });

    it('should dismiss modal if close or cancel button are clicked', () => {
        const dismissSpy = jest.spyOn(ngbActiveModal, 'dismiss');
        const closeButton = fixture.debugElement.query(By.css('button.btn-close'));
        expect(closeButton).not.toBeNull();
        closeButton.nativeElement.click();
        expect(dismissSpy).toHaveBeenCalledOnce();

        const cancelButton = fixture.debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(dismissSpy).toHaveBeenCalledTimes(2);
    });

    it('should return empty on finish when excel export is active', () => {
        component.activeTab = 1;
        const activeModalStub = jest.spyOn(ngbActiveModal, 'close');
        component.onFinish();
        expect(activeModalStub).toHaveBeenCalledWith();
    });

    it('should return the csv export options on finish when csv export is active', () => {
        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteStrings: CsvQuoteStrings.QUOTES_SINGLE,
            decimalSeparator: CsvDecimalSeparator.COMMA,
        };
        component.options = testOptions;
        component.activeTab = 2;
        const activeModalStub = jest.spyOn(ngbActiveModal, 'close');
        component.onFinish();
        expect(activeModalStub).toHaveBeenCalledWith(testOptions);
    });
});
