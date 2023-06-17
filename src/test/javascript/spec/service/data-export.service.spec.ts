import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { DataExport } from 'app/entities/data-export.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('DataExportService', () => {
    let service: DataExportService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(DataExportService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should make PUT request to request data export', fakeAsync(() => {
        const dataExport = new DataExport();
        dataExport.user = new User();
        dataExport.user.id = 1;
        dataExport.id = 1;
        dataExport.requestDate = dayjs();
        dataExport.creationDate = dayjs();
        service.requestDataExport().subscribe((resp) => expect(resp).toEqual(dataExport));
        const req = httpMock.expectOne({ method: 'PUT', url: `api/data-export` });
        req.flush(dataExport);
        tick();
    }));

    it('should make GET request to download data export', fakeAsync(() => {
        service.downloadDataExport(1).subscribe();
        const req = httpMock.expectOne({ method: 'GET', url: `api/data-export/1` });
        req.flush(new Blob());
        tick();
    }));
});
