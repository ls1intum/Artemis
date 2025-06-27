import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProgrammingExercisePagingService } from './programming-exercise-paging.service';

describe('ProgrammingExercisePagingService', () => {
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
        expect((service as any).resourceUrl).toBe('api/programming/programming-exercises');
    });
});
