import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PrivacyStatementUpdateComponent } from 'app/admin/privacy-statement/privacy-statement-update/privacy-statement-update.component';
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
import { PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';
import { PrivacyStatementService } from 'app/shared/service/privacy-statement.service';

describe('PrivacyStatementUpdateComponent', () => {
    let component: PrivacyStatementUpdateComponent;
    let fixture: ComponentFixture<PrivacyStatementUpdateComponent>;
    let modalService: NgbModal;
    let privacyStatementService: PrivacyStatementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                PrivacyStatementUpdateComponent,
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

        fixture = TestBed.createComponent(PrivacyStatementUpdateComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        privacyStatementService = TestBed.inject(PrivacyStatementService);
        fixture.detectChanges();
    });

    it('should show warning on language change with unsaved changes', () => {
        const open = jest.spyOn(modalService, 'open');
        component.unsavedChanges = true;
        component.currentLanguage = PrivacyStatementLanguage.ENGLISH;
        component.onLanguageChange(PrivacyStatementLanguage.GERMAN);
        fixture.detectChanges();
        expect(open).toHaveBeenCalledOnce();
        expect(open).toHaveBeenCalledWith(PrivacyStatementUnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
    });

    it('should load privacy statement in German on init', () => {
        const loadFile = jest.spyOn(privacyStatementService, 'getPrivacyStatementForUpdate');
        component.ngOnInit();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(PrivacyStatementLanguage.GERMAN);
    });

    it('should load privacy statement in selected language on language change', () => {
        const loadFile = jest.spyOn(privacyStatementService, 'getPrivacyStatementForUpdate');
        component.currentLanguage = PrivacyStatementLanguage.GERMAN;
        component.onLanguageChange(PrivacyStatementLanguage.ENGLISH);
        fixture.detectChanges();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(PrivacyStatementLanguage.ENGLISH);
        expect(component.currentLanguage).toEqual(PrivacyStatementLanguage.ENGLISH);
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
        component.privacyStatement.language = PrivacyStatementLanguage.GERMAN;
        component.privacyStatement.text = 'text';
        component.unsavedChanges = true;
        fixture.nativeElement.querySelector('#update-privacy-statement-btn').click();
        expect(updateFile).toHaveBeenCalledOnce();
        expect(updateFile).toHaveBeenCalledWith(component.privacyStatement);
        expect(component.unsavedChanges).toBeFalse();
    });
});
