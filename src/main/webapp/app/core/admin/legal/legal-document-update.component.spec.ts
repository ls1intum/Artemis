/**
 * Vitest tests for LegalDocumentUpdateComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Observable, of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';

import { LegalDocumentUpdateComponent } from 'app/core/admin/legal/legal-document-update.component';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/core/shared/entities/legal-document.model';
import { PrivacyStatement } from 'app/core/shared/entities/privacy-statement.model';
import { UnsavedChangesWarningComponent } from 'app/core/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';

describe('LegalDocumentUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LegalDocumentUpdateComponent;
    let fixture: ComponentFixture<LegalDocumentUpdateComponent>;
    let modalService: NgbModal;
    let legalDocumentService: LegalDocumentService;

    const defaultPrivacyStatement = new LegalDocument(LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentLanguage.GERMAN);
    defaultPrivacyStatement.text = 'default text';

    const mockModalService = {
        open: vi.fn(),
    };

    const mockLegalDocumentService = {
        getPrivacyStatementForUpdate: vi.fn().mockReturnValue(of(defaultPrivacyStatement)),
        getImprintForUpdate: vi.fn().mockReturnValue(of(new LegalDocument(LegalDocumentType.IMPRINT, LegalDocumentLanguage.GERMAN))),
        updatePrivacyStatement: vi.fn(),
        updateImprint: vi.fn(),
    };

    const mockLanguageHelper = {
        updateTitle: vi.fn(),
    };

    let mockActivatedRoute: { url: Observable<UrlSegment[]> };

    beforeEach(async () => {
        mockActivatedRoute = {
            url: of([{ path: 'privacy-statement' }] as UrlSegment[]),
        };

        await TestBed.configureTestingModule({
            imports: [LegalDocumentUpdateComponent],
            providers: [
                { provide: NgbModal, useValue: mockModalService },
                { provide: LegalDocumentService, useValue: mockLegalDocumentService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: JhiLanguageHelper, useValue: mockLanguageHelper },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(LegalDocumentUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(LegalDocumentUpdateComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        legalDocumentService = TestBed.inject(LegalDocumentService);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should show warning on language change with unsaved changes', () => {
        fixture.detectChanges();

        const mockReturnValue = {
            result: Promise.resolve({ type: LegalDocumentType.IMPRINT } as LegalDocument),
            componentInstance: {},
        } as NgbModalRef;
        const open = vi.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        component.unsavedChanges.set(true);
        component.currentLanguage.set(LegalDocumentLanguage.ENGLISH);

        component.onLanguageChange(LegalDocumentLanguage.GERMAN);

        expect(open).toHaveBeenCalledOnce();
        expect(open).toHaveBeenCalledWith(UnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
    });

    it('should load privacy statement in German on init', () => {
        const returnValue = new LegalDocument(LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentLanguage.GERMAN);
        returnValue.text = 'text';
        mockActivatedRoute.url = of([{ path: 'privacy-statement' }] as UrlSegment[]);
        vi.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate').mockReturnValue(of(returnValue));

        fixture.detectChanges();

        expect(legalDocumentService.getPrivacyStatementForUpdate).toHaveBeenCalledWith(LegalDocumentLanguage.GERMAN);
        expect(component.unsavedChanges()).toBe(false);
        expect(component.legalDocument()).toEqual(returnValue);
    });

    it('should load imprint in German on init', () => {
        const returnValue = new LegalDocument(LegalDocumentType.IMPRINT, LegalDocumentLanguage.GERMAN);
        returnValue.text = 'text';
        mockActivatedRoute.url = of([{ path: 'imprint' }] as UrlSegment[]);
        vi.spyOn(legalDocumentService, 'getImprintForUpdate').mockReturnValue(of(returnValue));

        fixture.detectChanges();

        expect(legalDocumentService.getImprintForUpdate).toHaveBeenCalledWith(LegalDocumentLanguage.GERMAN);
        expect(component.unsavedChanges()).toBe(false);
        expect(component.legalDocument()).toEqual(returnValue);
    });

    it('should load legal document in selected language on language change', () => {
        fixture.detectChanges();

        const returnValue = new LegalDocument(LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentLanguage.ENGLISH);
        returnValue.text = 'english text';
        vi.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate').mockReturnValue(of(returnValue));

        component.currentLanguage.set(LegalDocumentLanguage.GERMAN);
        component.unsavedChanges.set(false);

        component.onLanguageChange(LegalDocumentLanguage.ENGLISH);

        expect(legalDocumentService.getPrivacyStatementForUpdate).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
        expect(component.currentLanguage()).toEqual(LegalDocumentLanguage.ENGLISH);
        expect(component.unsavedChanges()).toBe(false);
    });

    it('should correctly determine unsaved changes', () => {
        fixture.detectChanges();

        component.unsavedChanges.set(false);
        const doc = component.legalDocument();
        if (doc) {
            doc.text = 'text';
        }

        component.onContentChanged('changed text');

        expect(component.unsavedChanges()).toBe(true);
    });

    it('should call update legal document service method on update', () => {
        fixture.detectChanges();

        const returnValue = new LegalDocument(LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentLanguage.GERMAN);
        returnValue.text = 'updated text';
        vi.spyOn(legalDocumentService, 'updatePrivacyStatement').mockReturnValue(of(returnValue));

        component.onContentChanged('updated text');

        component.updateLegalDocument();

        expect(legalDocumentService.updatePrivacyStatement).toHaveBeenCalledOnce();
        expect(component.unsavedChanges()).toBe(false);
        expect(component.isSaving()).toBe(false);
    });

    it('should set the value of the markdown editor when the language is changed while in preview mode', () => {
        fixture.detectChanges();

        const returnValue = new PrivacyStatement(LegalDocumentLanguage.GERMAN);
        returnValue.text = 'new content';

        component.unsavedChanges.set(false);
        vi.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate').mockReturnValue(of(returnValue));
        component.currentLanguage.set(LegalDocumentLanguage.ENGLISH);

        component.onLanguageChange(LegalDocumentLanguage.GERMAN);

        expect(legalDocumentService.getPrivacyStatementForUpdate).toHaveBeenCalledWith(LegalDocumentLanguage.GERMAN);
        expect(component.legalDocument()?.text).toBe('new content');
    });
});
