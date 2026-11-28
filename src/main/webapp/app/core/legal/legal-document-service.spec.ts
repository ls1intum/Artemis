import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { LegalDocumentLanguage, LegalDocumentType } from 'app/admin/legal/legal-document.model';
import { PrivacyStatement } from 'app/admin/legal/privacy-statement.model';
import { Imprint } from 'app/admin/legal/imprint.model';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

const legalDocuments = [
    {
        slug: 'privacy-statement',
        wireType: 'PRIVACY_STATEMENT',
        viewType: LegalDocumentType.PRIVACY_STATEMENT,
        Model: PrivacyStatement,
        read: 'getPrivacyStatement',
        edit: 'getPrivacyStatementForUpdate',
        save: 'updatePrivacyStatement',
    },
    {
        slug: 'imprint',
        wireType: 'IMPRINT',
        viewType: LegalDocumentType.IMPRINT,
        Model: Imprint,
        read: 'getImprint',
        edit: 'getImprintForUpdate',
        save: 'updateImprint',
    },
] as const;

describe('LegalDocumentService', () => {
    let service: LegalDocumentService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(LegalDocumentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it.each(legalDocuments)('should hydrate the returned language for a public $slug fallback', async ({ slug, wireType, viewType, Model, read }) => {
        const response = firstValueFrom(service[read](LegalDocumentLanguage.GERMAN));
        httpMock.expectOne({ method: 'GET', url: `/api/core/public/${slug}?language=de` }).flush({ type: wireType, language: 'ENGLISH', text: '# English text' });

        const document = await response;
        expect(document).toBeInstanceOf(Model);
        expect(document).toMatchObject({ type: viewType, language: LegalDocumentLanguage.ENGLISH, text: '# English text' });
    });

    it.each(legalDocuments)('should round-trip a $slug through the admin edit and save endpoints', async ({ slug, wireType, viewType, Model, edit, save }) => {
        const editResponse = firstValueFrom(service[edit](LegalDocumentLanguage.ENGLISH));
        httpMock.expectOne({ method: 'GET', url: `/api/admin/${slug}-for-update?language=en` }).flush({ type: wireType, language: 'ENGLISH' });

        const document = await editResponse;
        expect(document).toBeInstanceOf(Model);
        expect(document).toMatchObject({ type: viewType, language: LegalDocumentLanguage.ENGLISH });
        expect(document.text).toBeUndefined();

        const text = '# Edited text';
        const saveResponse = firstValueFrom(service[save](document, text));
        const request = httpMock.expectOne({ method: 'PUT', url: `/api/admin/${slug}` });
        expect(request.request.body).toStrictEqual({ type: wireType, language: 'ENGLISH', text });
        request.flush({ type: wireType, language: 'ENGLISH', text });

        const saved = await saveResponse;
        expect(saved).toBeInstanceOf(Model);
        expect(saved).toMatchObject({ type: viewType, language: LegalDocumentLanguage.ENGLISH, text });
    });

    it.each(legalDocuments)('should send German wire values when saving the German $slug', async ({ slug, wireType, Model, save }) => {
        const text = '# Bearbeiteter Text';
        const response = firstValueFrom(service[save](new Model(LegalDocumentLanguage.GERMAN), text));
        const request = httpMock.expectOne({ method: 'PUT', url: `/api/admin/${slug}` });
        expect(request.request.body).toStrictEqual({ type: wireType, language: 'GERMAN', text });
        request.flush({ type: wireType, language: 'GERMAN', text });

        expect((await response).language).toBe(LegalDocumentLanguage.GERMAN);
    });
});
