import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { PrivacyStatementService } from 'app/shared/service/privacy-statement.service';
import { PrivacyStatement } from 'app/entities/privacy-statement.model';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';

describe('PrivacyStatementService', () => {
    let service: PrivacyStatementService;
    const resourceUrl = SERVER_API_URL + 'api';
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(PrivacyStatementService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should make PUT request to update privacy statement', fakeAsync(() => {
        const update = new PrivacyStatement(LegalDocumentLanguage.ENGLISH);
        update.text = 'updated text';
        service.updatePrivacyStatement(update).subscribe((resp) => expect(resp).toEqual(update));
        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/privacy-statement` });
        req.flush(update);
        tick();
    }));

    it('should make GET request to retrieve privacy statement for update', fakeAsync(() => {
        const lang = LegalDocumentLanguage.ENGLISH;
        const expected = new PrivacyStatement(lang);
        expected.text = 'text';
        service.getPrivacyStatementForUpdate(lang).subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/privacy-statement-for-update?language=${lang}` });
        req.flush(expected);
        tick();
    }));

    it('should make GET request to retrieve privacy statement', fakeAsync(() => {
        const lang = LegalDocumentLanguage.GERMAN;
        const expected = new PrivacyStatement(lang);
        expected.text = 'text';
        service.getPrivacyStatement(lang).subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/privacy-statement?language=${lang}` });
        req.flush(expected);
        tick();
    }));
});
