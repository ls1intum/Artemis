/**
 * Vitest tests for LogsService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LogsService } from 'app/core/admin/logs/logs.service';
import { Log } from 'app/core/admin/logs/log.model';

describe('LogsService', () => {
    setupTestBed({ zoneless: true });

    let service: LogsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(LogsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should call correct URL for findAll', () => {
        const resourceUrl = 'management/loggers';

        service.findAll().subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe(resourceUrl);
    });

    it('should return loggers response', () => {
        const mockResponse = {
            loggers: {
                main: { effectiveLevel: 'ERROR' },
            },
        };

        service.findAll().subscribe((resp) => {
            expect(resp).toEqual(mockResponse);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(mockResponse);
    });

    it('should change log level', () => {
        const log = new Log('test', 'ERROR');

        service.changeLevel(log.name, log.level).subscribe((received) => {
            expect(received).toEqual(log);
        });

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.url).toBe('management/loggers/test');
        expect(req.request.body).toEqual({ configuredLevel: 'ERROR' });
        req.flush(log);
    });
});
