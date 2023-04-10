import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PrivacyStatement } from 'app/entities/privacy-statement.model';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { ImprintService } from 'app/shared/service/imprint.service';
import { Imprint } from 'app/entities/imprint.model';

describe('ImprintService', () => {
    let service: ImprintService;
    const resourceUrl = SERVER_API_URL + 'api';
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(ImprintService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should make PUT request to update imprint', fakeAsync(() => {
        const update = new Imprint(LegalDocumentLanguage.ENGLISH);
        update.text = 'updated text';
        service.updateImprint(update).subscribe((resp) => expect(resp).toEqual(update));
        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/imprint` });
        req.flush(update);
        tick();
    }));

    it('should make GET request to retrieve imprint for update', fakeAsync(() => {
        const lang = LegalDocumentLanguage.ENGLISH;
        const expected = new Imprint(lang);
        expected.text = 'text';
        service.getImprintForUpdate(lang).subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/imprint-for-update?language=${lang}` });
        req.flush(expected);
        tick();
    }));

    it('should make GET request to retrieve imprint', fakeAsync(() => {
        const lang = LegalDocumentLanguage.GERMAN;
        const expected = new PrivacyStatement(lang);
        expected.text = 'text';
        service.getImprint(lang).subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/imprint?language=${lang}` });
        req.flush(expected);
        tick();
    }));
});
