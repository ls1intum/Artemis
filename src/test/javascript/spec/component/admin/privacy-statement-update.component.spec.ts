import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LegalDocumentUpdateComponent } from 'app/admin/legal/legal-document-update.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { UnsavedChangesWarningComponent } from 'app/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTestModule } from '../../test.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockLanguageHelper } from '../../helpers/mocks/service/mock-translate.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ModePickerComponent } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';

describe('PrivacyStatementUpdateComponent', () => {
    let component: LegalDocumentUpdateComponent;
    let fixture: ComponentFixture<LegalDocumentUpdateComponent>;
    let modalService: NgbModal;
    let privacyStatementService: LegalDocumentService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                LegalDocumentUpdateComponent,
                MockComponent(UnsavedChangesWarningComponent),
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

        fixture = TestBed.createComponent(LegalDocumentUpdateComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        privacyStatementService = TestBed.inject(LegalDocumentService);
        fixture.detectChanges();
    });

    it('should show warning on language change with unsaved changes', () => {
        const open = jest.spyOn(modalService, 'open');
        component.unsavedChanges = true;
        component.currentLanguage = LegalDocumentLanguage.ENGLISH;
        component.onLanguageChange(LegalDocumentLanguage.GERMAN);
        fixture.detectChanges();
        expect(open).toHaveBeenCalledOnce();
        expect(open).toHaveBeenCalledWith(UnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
    });

    it('should load privacy statement in German on init', () => {
        const loadFile = jest.spyOn(privacyStatementService, 'getPrivacyStatementForUpdate');
        component.ngOnInit();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.GERMAN);
    });

    it('should load privacy statement in selected language on language change', () => {
        const loadFile = jest.spyOn(privacyStatementService, 'getPrivacyStatementForUpdate');
        component.currentLanguage = LegalDocumentLanguage.GERMAN;
        component.onLanguageChange(LegalDocumentLanguage.ENGLISH);
        fixture.detectChanges();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
        expect(component.currentLanguage).toEqual(LegalDocumentLanguage.ENGLISH);
    });

    it('should correctly determine unsaved changes', () => {
        component.unsavedChanges = false;
        component.privacyStatement.text = 'text';
        component.checkUnsavedChanges('changed text');
        expect(component.unsavedChanges).toBeTrue();
    });

    it('should update privacy statement when clicking save', () => {
        const updateFile = jest.spyOn(privacyStatementService, 'updatePrivacyStatement');
        component.privacyStatement.text = 'Datenschutzerklärung';
        component.privacyStatement.language = LegalDocumentLanguage.GERMAN;
        component.privacyStatement.text = 'text';
        component.unsavedChanges = true;
        fixture.nativeElement.querySelector('#update-privacy-statement-btn').click();
        expect(updateFile).toHaveBeenCalledOnce();
        expect(updateFile).toHaveBeenCalledWith(component.privacyStatement);
    });
});
