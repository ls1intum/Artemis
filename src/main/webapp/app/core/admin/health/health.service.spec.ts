/**
 * Vitest tests for HealthService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { HealthService } from 'app/core/admin/health/health.service';
import { Health } from 'app/core/admin/health/health.model';

describe('HealthService', () => {
    setupTestBed({ zoneless: true });

    let service: HealthService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(HealthService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('checkHealth', () => {
        it('should return health status with UP state', () => {
            const mockHealth: Health = {
                status: 'UP',
                components: {
                    diskSpace: {
                        status: 'UP',
                        details: {
                            total: 499963174912,
                            free: 250000000000,
                            threshold: 10485760,
                        },
                    },
                    mail: {
                        status: 'UP',
                        details: {
                            location: 'smtp.example.com:587',
                        },
                    },
                    ping: {
                        status: 'UP',
                    },
                    livenessState: {
                        status: 'UP',
                    },
                    readinessState: {
                        status: 'UP',
                    },
                    db: {
                        status: 'UP',
                        details: {
                            database: 'MySQL',
                            validationQuery: 'isValid()',
                        },
                    },
                },
            };

            let result: Health | undefined;
            service.checkHealth().subscribe((received) => {
                result = received;
            });

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toBe('management/health');
            req.flush(mockHealth);
            expect(result).toEqual(mockHealth);
        });

        it('should return health status with DOWN state', () => {
            const mockHealth: Health = {
                status: 'DOWN',
                components: {
                    db: {
                        status: 'DOWN',
                        details: {
                            error: 'Connection refused',
                        },
                    },
                },
            };

            let result: Health | undefined;
            service.checkHealth().subscribe((received) => {
                result = received;
            });

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toBe('management/health');
            req.flush(mockHealth);
            expect(result).toEqual(mockHealth);
            expect(result?.status).toBe('DOWN');
        });

        it('should handle partial health response', () => {
            const mockHealth: Health = {
                status: 'UP',
                components: {
                    ping: {
                        status: 'UP',
                    },
                },
            };

            let result: Health | undefined;
            service.checkHealth().subscribe((received) => {
                result = received;
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockHealth);
            expect(result?.components.ping?.status).toBe('UP');
            expect(result?.components.db).toBeUndefined();
        });

        it('should handle UNKNOWN status', () => {
            const mockHealth: Health = {
                status: 'UNKNOWN',
                components: {},
            };

            let result: Health | undefined;
            service.checkHealth().subscribe((received) => {
                result = received;
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockHealth);
            expect(result?.status).toBe('UNKNOWN');
        });

        it('should handle OUT_OF_SERVICE status', () => {
            const mockHealth: Health = {
                status: 'OUT_OF_SERVICE',
                components: {
                    mail: {
                        status: 'OUT_OF_SERVICE',
                        details: {
                            reason: 'Maintenance mode',
                        },
                    },
                },
            };

            let result: Health | undefined;
            service.checkHealth().subscribe((received) => {
                result = received;
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockHealth);
            expect(result?.status).toBe('OUT_OF_SERVICE');
            expect(result?.components.mail?.status).toBe('OUT_OF_SERVICE');
        });
    });
});
