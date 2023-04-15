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
import { LegalDocumentLanguage, LegalDocumentType } from 'app/entities/legal-document.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { of } from 'rxjs';

describe('LegalDocumentUpdateComponent', () => {
    let component: LegalDocumentUpdateComponent;
    let fixture: ComponentFixture<LegalDocumentUpdateComponent>;
    let modalService: NgbModal;
    let legalDocumentService: LegalDocumentService;
    let route: ActivatedRoute;

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
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LegalDocumentUpdateComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        legalDocumentService = TestBed.inject(LegalDocumentService);
        route = TestBed.inject(ActivatedRoute);
        setupRoutes(LegalDocumentType.PRIVACY_STATEMENT);
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

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])('should load legal document in German on init', (documentType: LegalDocumentType) => {
        setupRoutes(documentType);
        let loadFile;
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            loadFile = jest.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate');
        } else {
            loadFile = jest.spyOn(legalDocumentService, 'getImprintForUpdate');
        }
        component.ngOnInit();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.GERMAN);
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should load legal document in selected language on language change',
        (documentType: LegalDocumentType) => {
            console.log('documentType', documentType);
            setupRoutes(documentType);
            component.ngOnInit();
            let loadFile;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                loadFile = jest.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate');
            } else {
                loadFile = jest.spyOn(legalDocumentService, 'getImprintForUpdate');
            }
            component.currentLanguage = LegalDocumentLanguage.GERMAN;
            component.unsavedChanges = false;
            component.onLanguageChange(LegalDocumentLanguage.ENGLISH);
            fixture.detectChanges();
            expect(loadFile).toHaveBeenCalledOnce();
            expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
            expect(component.currentLanguage).toEqual(LegalDocumentLanguage.ENGLISH);
        },
    );

    it('should correctly determine unsaved changes', () => {
        component.unsavedChanges = false;
        component.legalDocument.text = 'text';
        component.checkUnsavedChanges('changed text');
        expect(component.unsavedChanges).toBeTrue();
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])('should update legal document when clicking save', (documentType: LegalDocumentType) => {
        setupRoutes(documentType);
        component.ngOnInit();
        let updateFile;
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            updateFile = jest.spyOn(legalDocumentService, 'updatePrivacyStatement');
        } else {
            updateFile = jest.spyOn(legalDocumentService, 'updateImprint');
        }
        component.legalDocument.language = LegalDocumentLanguage.GERMAN;
        component.legalDocument.text = 'text';
        component.unsavedChanges = true;
        fixture.nativeElement.querySelector('#update-legal-document-btn').click();
        expect(updateFile).toHaveBeenCalledOnce();
        expect(updateFile).toHaveBeenCalledWith(component.legalDocument);
    });

    function setupRoutes(documentType: LegalDocumentType) {
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            // @ts-ignore
            route.parent.url = of([{ path: 'privacy-statement' }] as UrlSegment[]);
        } else {
            // @ts-ignore
            route.parent.url = of([{ path: 'imprint' }] as UrlSegment[]);
        }
    }
});
