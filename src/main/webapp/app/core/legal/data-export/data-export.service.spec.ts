import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { DataExport } from 'app/core/shared/entities/data-export.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';

describe('DataExportService', () => {
    setupTestBed({ zoneless: true });

    let service: DataExportService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(DataExportService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should make POST request to request data export', async () => {
        const dataExport = new DataExport();
        dataExport.user = new User();
        dataExport.user.id = 1;
        dataExport.id = 1;
        dataExport.createdDate = dayjs();
        dataExport.creationFinishedDate = dayjs();
        const promise = firstValueFrom(service.requestDataExport());
        const req = httpMock.expectOne({ method: 'POST', url: `api/core/data-exports` });
        req.flush(dataExport);
        const resp = await promise;
        expect(resp).toEqual(dataExport);
    });

    it('should make open download link to download data export', () => {
        const windowSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        service.downloadDataExport(1);
        expect(windowSpy).toHaveBeenCalledWith('api/core/data-exports/1', '_blank');
    });

    it('should make POST request to request data export as admin for another user', async () => {
        const dataExport = new DataExport();
        const user = new User();
        user.login = 'ge12abc';
        const promise = firstValueFrom(service.requestDataExportForAnotherUser(user.login));
        const req = httpMock.expectOne({ method: 'POST', url: `api/core/admin/data-exports/ge12abc` });
        req.flush(dataExport);
        const resp = await promise;
        expect(resp).toEqual(dataExport);
    });

    it('should make GET request to check if any data export can be downloaded', async () => {
        const promise = firstValueFrom(service.canDownloadAnyDataExport());
        const req = httpMock.expectOne({ method: 'GET', url: `api/core/data-exports/can-download` });
        req.flush(true);
        await promise;
    });

    it('should make GET request to check if a specific data export can be downloaded', async () => {
        const promise = firstValueFrom(service.canDownloadSpecificDataExport(1));
        const req = httpMock.expectOne({ method: 'GET', url: `api/core/data-exports/1/can-download` });
        req.flush(true);
        await promise;
    });

    it('should make GET request to check if a data export can be requested', async () => {
        const promise = firstValueFrom(service.canRequestDataExport());
        const req = httpMock.expectOne({ method: 'GET', url: `api/core/data-exports/can-request` });
        req.flush(true);
        await promise;
    });
});
