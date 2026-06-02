import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProgrammingExercisePagingService } from './programming-exercise-paging.service';

/**
 * Typed view onto the protected `resourceUrl` member (inherited from {@link ExercisePagingService})
 * so the spec can assert on it without a blanket `(service as any)` cast.
 */
type PagingServiceInternals = ProgrammingExercisePagingService & { resourceUrl: string };
const internals = (s: ProgrammingExercisePagingService): PagingServiceInternals => s as PagingServiceInternals;

describe('ProgrammingExercisePagingService', () => {
    setupTestBed({ zoneless: true });

    let service: ProgrammingExercisePagingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), ProgrammingExercisePagingService],
        });

        service = TestBed.inject(ProgrammingExercisePagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should have correct resource URL', () => {
        expect(ProgrammingExercisePagingService.RESOURCE_URL).toBe('api/programming/programming-exercises');
    });

    it('should initialize with correct resource URL', () => {
        expect(internals(service).resourceUrl).toBe('api/programming/programming-exercises');
    });
});
