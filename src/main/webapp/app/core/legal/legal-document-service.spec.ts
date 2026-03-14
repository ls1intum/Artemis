import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/core/shared/entities/legal-document.model';
import { PrivacyStatement } from 'app/core/shared/entities/privacy-statement.model';
import { Imprint } from 'app/core/shared/entities/imprint.model';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

describe('LegalDocumentService', () => {
    setupTestBed({ zoneless: true });

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
        vi.restoreAllMocks();
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])('should make PUT request to update legal document', async (documentType: LegalDocumentType) => {
        let update: LegalDocument;
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            update = new PrivacyStatement(LegalDocumentLanguage.ENGLISH);
        } else {
            update = new Imprint(LegalDocumentLanguage.ENGLISH);
        }
        update.text = 'updated text';
        let req;
        let promise;
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            promise = firstValueFrom(service.updatePrivacyStatement(update));
            req = httpMock.expectOne({ method: 'PUT', url: 'api/core/admin/privacy-statement' });
        } else {
            promise = firstValueFrom(service.updateImprint(update));
            req = httpMock.expectOne({ method: 'PUT', url: 'api/core/admin/imprint' });
        }
        req.flush(update);
        const resp = await promise;
        expect(resp).toEqual(update);
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should make GET request to retrieve legal document for update',
        async (documentType: LegalDocumentType) => {
            const lang = LegalDocumentLanguage.ENGLISH;
            let expected: LegalDocument;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                expected = new PrivacyStatement(lang);
            } else {
                expected = new Imprint(lang);
            }
            expected.text = 'text';
            let req;
            let promise;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                promise = firstValueFrom(service.getPrivacyStatementForUpdate(lang));
                req = httpMock.expectOne({ method: 'GET', url: `api/core/admin/privacy-statement-for-update?language=${lang}` });
            } else {
                promise = firstValueFrom(service.getImprintForUpdate(lang));
                req = httpMock.expectOne({ method: 'GET', url: `api/core/admin/imprint-for-update?language=${lang}` });
            }
            req.flush(expected);
            const resp = await promise;
            expect(resp).toEqual(expected);
        },
    );

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])('should make GET request to retrieve legal document', async (documentType: LegalDocumentType) => {
        const lang = LegalDocumentLanguage.GERMAN;
        let expected: LegalDocument;
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            expected = new PrivacyStatement(lang);
        } else {
            expected = new Imprint(lang);
        }
        expected.text = 'text';
        let req;
        let promise;
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            promise = firstValueFrom(service.getPrivacyStatement(lang));
            req = httpMock.expectOne({ method: 'GET', url: `api/core/public/privacy-statement?language=${lang}` });
        } else {
            promise = firstValueFrom(service.getImprint(lang));
            req = httpMock.expectOne({ method: 'GET', url: `api/core/public/imprint?language=${lang}` });
        }
        req.flush(expected);
        const resp = await promise;
        expect(resp).toEqual(expected);
    });
});
