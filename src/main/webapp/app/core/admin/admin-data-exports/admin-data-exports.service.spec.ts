/**
 * Vitest tests for AdminDataExportsService.
 *
 * Tests cover:
 * - Fetching all data exports with pagination and date conversion
 * - Requesting data exports for users (scheduled and immediate)
 * - Download functionality
 */
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { firstValueFrom } from 'rxjs';
import dayjs from 'dayjs/esm';
import { AdminDataExportsService } from 'app/core/admin/admin-data-exports/admin-data-exports.service';
import { AdminDataExport, DataExportState } from 'app/core/shared/entities/data-export.model';

describe('AdminDataExportsService', () => {
    setupTestBed({ zoneless: true });

    let service: AdminDataExportsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(AdminDataExportsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get all data exports with pagination', async () => {
        const mockExports: AdminDataExport[] = [
            {
                id: 1,
                userId: 1,
                userLogin: 'user1',
                userName: 'User One',
                dataExportState: DataExportState.EMAIL_SENT,
                createdDate: dayjs('2024-01-01'),
                creationFinishedDate: dayjs('2024-01-01'),
                downloadable: true,
            },
            {
                id: 2,
                userId: 2,
                userLogin: 'user2',
                userName: 'User Two',
                dataExportState: DataExportState.REQUESTED,
                createdDate: dayjs('2024-01-02'),
                downloadable: false,
            },
        ];

        const exportPromise = firstValueFrom(service.getAllDataExports(0, 20));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/core/admin/data-exports?page=0&size=20' });
        req.flush(mockExports, {
            headers: { 'X-Total-Count': '50' },
        });

        const result = await exportPromise;
        expect(result.content).toHaveLength(2);
        expect(result.totalElements).toBe(50);
        expect(result.totalPages).toBe(3); // 50 / 20 = 2.5, ceil = 3
        expect(result.content[0].userLogin).toBe('user1');
        expect(result.content[1].userLogin).toBe('user2');
    });

    it('should request data export for user with executeNow=false', async () => {
        const login = 'testuser';

        const exportPromise = firstValueFrom(service.requestDataExportForUser(login, false));

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/core/admin/data-exports/${login}?executeNow=false`,
        });
        req.flush({});

        await exportPromise;
    });

    it('should request data export for user with executeNow=true', async () => {
        const login = 'testuser';

        const exportPromise = firstValueFrom(service.requestDataExportForUser(login, true));

        const req = httpMock.expectOne({
            method: 'POST',
            url: `api/core/admin/data-exports/${login}?executeNow=true`,
        });
        req.flush({});

        await exportPromise;
    });

    it('should open download link in new window', () => {
        const windowSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        const dataExportId = 123;

        service.downloadDataExport(dataExportId);

        expect(windowSpy).toHaveBeenCalledWith('api/core/admin/data-exports/123/download', '_blank');
    });

    it('should convert date strings to dayjs objects', async () => {
        const mockExport: AdminDataExport = {
            id: 1,
            userId: 1,
            userLogin: 'user1',
            userName: 'User One',
            dataExportState: DataExportState.EMAIL_SENT,
            createdDate: '2024-01-15T10:30:00Z' as unknown as dayjs.Dayjs,
            creationFinishedDate: '2024-01-15T11:00:00Z' as unknown as dayjs.Dayjs,
            downloadable: true,
        };

        const exportPromise = firstValueFrom(service.getAllDataExports());

        const req = httpMock.expectOne({ method: 'GET', url: 'api/core/admin/data-exports?page=0&size=20' });
        req.flush([mockExport], {
            headers: { 'X-Total-Count': '1' },
        });

        const result = await exportPromise;
        expect(dayjs.isDayjs(result.content[0].createdDate)).toBe(true);
        expect(dayjs.isDayjs(result.content[0].creationFinishedDate)).toBe(true);
    });

    it('should use default pagination values', async () => {
        const exportPromise = firstValueFrom(service.getAllDataExports());

        const req = httpMock.expectOne({ method: 'GET', url: 'api/core/admin/data-exports?page=0&size=20' });
        req.flush([], { headers: { 'X-Total-Count': '0' } });

        const result = await exportPromise;
        expect(result.content).toHaveLength(0);
        expect(result.totalElements).toBe(0);
    });

    it('should handle custom pagination values', async () => {
        const exportPromise = firstValueFrom(service.getAllDataExports(2, 50));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/core/admin/data-exports?page=2&size=50' });
        req.flush([], { headers: { 'X-Total-Count': '100' } });

        const result = await exportPromise;
        expect(result.totalPages).toBe(2); // 100 / 50 = 2
    });
});
