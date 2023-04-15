import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/entities/legal-document.model';

describe('LegalDocumentService', () => {
    let service: LegalDocumentService;
    const resourceUrl = SERVER_API_URL + 'api';
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
            const update = new LegalDocument(documentType, LegalDocumentLanguage.ENGLISH);
            update.text = 'updated text';
            let req;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                service.updatePrivacyStatement(update).subscribe((resp) => expect(resp).toEqual(update));
                req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/privacy-statement` });
            } else {
                service.updateImprint(update).subscribe((resp) => expect(resp).toEqual(update));
                req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/imprint` });
            }
            req.flush(update);
            tick();
        }),
    );

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should make GET request to retrieve legal document for update',
        fakeAsync((documentType: LegalDocumentType) => {
            const lang = LegalDocumentLanguage.ENGLISH;
            const expected = new LegalDocument(documentType, lang);
            expected.text = 'text';
            let req;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                service.getPrivacyStatementForUpdate(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/privacy-statement-for-update?language=${lang}` });
            } else {
                service.getImprintForUpdate(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/imprint-for-update?language=${lang}` });
            }
            req.flush(expected);
            tick();
        }),
    );

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should make GET request to retrieve legal document',
        fakeAsync((documentType: LegalDocumentType) => {
            const lang = LegalDocumentLanguage.GERMAN;
            const expected = new LegalDocument(documentType, lang);
            expected.text = 'text';
            let req;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                service.getPrivacyStatement(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/privacy-statement?language=${lang}` });
            } else {
                service.getImprint(lang).subscribe((resp) => expect(resp).toEqual(expected));
                req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/imprint?language=${lang}` });
            }
            req.flush(expected);
            tick();
        }),
    );
});
