/**
 * Vitest tests for MetricsService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { MetricsService } from 'app/core/admin/metrics/metrics.service';
import { ThreadDump, ThreadState } from 'app/core/admin/metrics/metrics.model';

describe('MetricsService', () => {
    setupTestBed({ zoneless: true });

    let service: MetricsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(MetricsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should return metrics', () => {
        const metrics = {
            jvm: {},
            'http.server.requests': {},
            cache: {},
            services: {},
            databases: {},
            garbageCollector: {},
            processMetrics: {},
        };

        let result: any;
        service.getMetrics().subscribe((received) => {
            result = received;
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('management/jhimetrics');
        req.flush(metrics);
        expect(result).toEqual(metrics);
    });

    it('should return thread dump', () => {
        const dump: ThreadDump = {
            threads: [
                {
                    threadName: 'Reference Handler',
                    threadId: 2,
                    blockedTime: -1,
                    blockedCount: 7,
                    waitedTime: -1,
                    waitedCount: 0,
                    lockName: undefined,
                    lockOwnerId: -1,
                    lockOwnerName: undefined,
                    daemon: true,
                    inNative: false,
                    suspended: false,
                    threadState: ThreadState.Runnable,
                    priority: 10,
                    stackTrace: [],
                    lockedMonitors: [],
                    lockedSynchronizers: [],
                    lockInfo: undefined,
                },
            ],
        };

        let result: ThreadDump | undefined;
        service.threadDump().subscribe((received) => {
            result = received;
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('management/threaddump');
        req.flush(dump);
        expect(result).toEqual(dump);
    });
});
