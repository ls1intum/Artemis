import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { BuildLogService } from 'app/programming/shared/services/build-log.service';
import { BuildLogEntry, BuildLogType } from 'app/buildagent/shared/entities/build-log.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('Build Log Service', () => {
    setupTestBed({ zoneless: true });

    let service: BuildLogService;
    let httpMock: HttpTestingController;

    const resourceUrl = 'api/programming/participations/42/buildlogs';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(BuildLogService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should return a specific results build logs', async () => {
            const logEntry: BuildLogEntry = { time: 'some time', log: 'compilation error', type: BuildLogType.ERROR };

            service.getBuildLogs(42, 123).subscribe((returnedLogEntry) => expect(returnedLogEntry).toEqual(logEntry));

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toEqual(resourceUrl);
            expect(req.request.params.keys()).toHaveLength(1);
            expect(req.request.params.get('resultId')).toBe('123');
            req.flush(logEntry);
        });

        it('should not pass request parameters if result ID not specified', async () => {
            service.getBuildLogs(42).subscribe();

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toEqual(resourceUrl);
            expect(req.request.params.keys()).toHaveLength(0);
            expect(req.request.params.keys()).not.toContain('resultId');
            req.flush(null);
        });
    });
});
