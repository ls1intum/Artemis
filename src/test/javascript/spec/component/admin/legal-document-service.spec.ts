import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/entities/legal-document.model';
import { PrivacyStatement } from 'app/entities/privacy-statement.model';
import { Imprint } from 'app/entities/imprint.model';

describe('LegalDocumentService', () => {
    let service: LegalDocumentService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(LegalDocumentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should make PUT request to update legal document',
        fakeAsync((documentType: LegalDocumentType) => {
            let update: LegalDocument;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                update = new PrivacyStatement(LegalDocumentLanguage.ENGLISH);
            } else {
                update = new Imprint(LegalDocumentLanguage.ENGLISH);
            }
            update.text = 'updated text';
            let req;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                service.updatePrivacyStatement(update).subscribe((resp) => expect(resp).toEqual(update));
                req = httpMock.expectOne({ method: 'PUT', url: 'api/admin/privacy-statement' });
            } else {
                service.updateImprint(update).subscribe((resp) => expect(resp).toEqual(update));
                req = httpMock.expectOne({ method: 'PUT', url: 'api/admin/imprint' });
            }
            req.flush(update);
            tick();
        }),
    );

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should make GET request to retrieve legal document for update',
        fakeAsync((documentType: LegalDocumentType) => {
            const lang = LegalDocumentLanguage.ENGLISH;
            let expected: LegalDocument;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                expected = new PrivacyStatement(lang);
            } else {
                expected = new Imprint(lang);
            }
            expected.text = 'text';
            let req;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                service.getPrivacyStatementForUpdate(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `api/admin/privacy-statement-for-update?language=${lang}` });
            } else {
                service.getImprintForUpdate(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `api/admin/imprint-for-update?language=${lang}` });
            }
            req.flush(expected);
            tick();
        }),
    );

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should make GET request to retrieve legal document',
        fakeAsync((documentType: LegalDocumentType) => {
            const lang = LegalDocumentLanguage.GERMAN;
            let expected: LegalDocument;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                expected = new PrivacyStatement(lang);
            } else {
                expected = new Imprint(lang);
            }
            expected.text = 'text';
            let req;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                service.getPrivacyStatement(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `api/public/privacy-statement?language=${lang}` });
            } else {
                service.getImprint(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `api/public/imprint?language=${lang}` });
            }
            req.flush(expected);
            tick();
        }),
    );
});
