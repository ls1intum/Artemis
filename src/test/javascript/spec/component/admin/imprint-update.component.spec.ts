import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective } from 'ng-mocks';
import { PrivacyStatementUnsavedChangesWarningComponent } from 'app/admin/privacy-statement/unsaved-changes-warning/privacy-statement-unsaved-changes-warning.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTestModule } from '../../test.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockLanguageHelper } from '../../helpers/mocks/service/mock-translate.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ModePickerComponent } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { ImprintUpdateComponent } from 'app/admin/imprint/imprint-update/imprint-update.component';
import { ImprintService } from 'app/shared/service/imprint.service';

describe('ImprintUpdateComponent', () => {
    let component: ImprintUpdateComponent;
    let fixture: ComponentFixture<ImprintUpdateComponent>;
    let modalService: NgbModal;
    let imprintService: ImprintService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ImprintUpdateComponent,
                MockComponent(PrivacyStatementUnsavedChangesWarningComponent),
                MockComponent(ButtonComponent),
                MockDirective(TranslateDirective),
                MockComponent(MarkdownEditorComponent),
                MockComponent(ModePickerComponent),
            ],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ImprintUpdateComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        imprintService = TestBed.inject(ImprintService);
        fixture.detectChanges();
    });

    it('should show warning on language change with unsaved changes', () => {
        const open = jest.spyOn(modalService, 'open');
        component.unsavedChanges = true;
        component.currentLanguage = LegalDocumentLanguage.ENGLISH;
        component.onLanguageChange(LegalDocumentLanguage.GERMAN);
        fixture.detectChanges();
        expect(open).toHaveBeenCalledOnce();
        expect(open).toHaveBeenCalledWith(PrivacyStatementUnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
    });

    it('should load imprint in German on init', () => {
        const loadFile = jest.spyOn(imprintService, 'getImprintForUpdate');
        component.ngOnInit();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.GERMAN);
    });

    it('should load imprint in selected language on language change', () => {
        const loadFile = jest.spyOn(imprintService, 'getImprintForUpdate');
        component.currentLanguage = LegalDocumentLanguage.GERMAN;
        component.onLanguageChange(LegalDocumentLanguage.ENGLISH);
        fixture.detectChanges();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
        expect(component.currentLanguage).toEqual(LegalDocumentLanguage.ENGLISH);
    });

    it('should correctly determine unsaved changes', () => {
        component.unsavedChanges = false;
        component.imprint.text = 'text';
        component.checkUnsavedChanges('changed text');
        expect(component.unsavedChanges).toBeTrue();
    });

    it('should update imprint when clicking save', () => {
        const updateFile = jest.spyOn(imprintService, 'updateImprint');
        component.imprint.text = 'Impressum';
        component.imprint.language = LegalDocumentLanguage.GERMAN;
        component.imprint.text = 'text';
        component.unsavedChanges = true;
        fixture.nativeElement.querySelector('#update-imprint-btn').click();
        expect(updateFile).toHaveBeenCalledOnce();
        expect(updateFile).toHaveBeenCalledWith(component.imprint);
    });
});
