import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CsvDecimalSeparator, CsvExportModalComponent, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings } from 'app/shared/export/csv-export-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CsvExportModalComponent', () => {
    let component: CsvExportModalComponent;
    let fixture: ComponentFixture<CsvExportModalComponent>;
    let ngbActiveModal: NgbActiveModal;
    let translateService: TranslateService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [CsvExportModalComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbActiveModal), MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CsvExportModalComponent);
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

    it('should return the export options on finish', () => {
        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteStrings: CsvQuoteStrings.QUOTES_SINGLE,
            decimalSeparator: CsvDecimalSeparator.COMMA,
        };
        component.options = testOptions;
        const activeModalStub = jest.spyOn(ngbActiveModal, 'close');
        component.onFinish();
        expect(activeModalStub).toHaveBeenCalledWith(testOptions);
    });
});
