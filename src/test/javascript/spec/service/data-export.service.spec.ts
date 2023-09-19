import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { DataExport } from 'app/entities/data-export.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
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

    it('should make POST request to request data export', fakeAsync(() => {
        const dataExport = new DataExport();
        dataExport.user = new User();
        dataExport.user.id = 1;
        dataExport.id = 1;
        dataExport.createdDate = dayjs();
        dataExport.creationFinishedDate = dayjs();
        service.requestDataExport().subscribe((resp) => expect(resp).toEqual(dataExport));
        const req = httpMock.expectOne({ method: 'POST', url: `api/data-exports` });
        req.flush(dataExport);
        tick();
    }));

    it('should make open download link to download data export', () => {
        const windowSpy = jest.spyOn(window, 'open').mockImplementation();
        service.downloadDataExport(1);
        expect(windowSpy).toHaveBeenCalledWith('api/data-exports/1', '_blank');
    });

    it('should make POST request to request data export as admin for another user', fakeAsync(() => {
        const dataExport = new DataExport();
        const user = new User();
        user.login = 'ge12abc';
        service.requestDataExportForAnotherUser(user.login).subscribe((resp) => expect(resp).toEqual(dataExport));
        const req = httpMock.expectOne({ method: 'POST', url: `api/admin/data-exports/ge12abc` });
        req.flush(dataExport);
        tick();
    }));

    it('should make GET request to check if any data export can be downloaded', fakeAsync(() => {
        service.canDownloadAnyDataExport().subscribe();
        const req = httpMock.expectOne({ method: 'GET', url: `api/data-exports/can-download` });
        req.flush(true);
        tick();
    }));

    it('should make GET request to check if a specific data export can be downloaded', fakeAsync(() => {
        service.canDownloadSpecificDataExport(1).subscribe();
        const req = httpMock.expectOne({ method: 'GET', url: `api/data-exports/1/can-download` });
        req.flush(true);
        tick();
    }));

    it('should make GET request to check if a data export can be requested', fakeAsync(() => {
        service.canRequestDataExport().subscribe();
        const req = httpMock.expectOne({ method: 'GET', url: `api/data-exports/can-request` });
        req.flush(true);
        tick();
    }));
});
