import { afterEach, beforeEach, describe, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/core/course/overview/visualizations/exercise-scores-chart.service';
import { provideHttpClient } from '@angular/common/http';

describe('Exercise Scores Chart Service', () => {
    setupTestBed({ zoneless: true });
    let service: ExerciseScoresChartService;
    let httpMock: HttpTestingController;
    let elemDefault: ExerciseScoresDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(ExerciseScoresChartService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new ExerciseScoresDTO();
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should find all by course id', () => {
        const returnedFromService = [
            Object.assign(
                {
                    id: 0,
                },
                elemDefault,
            ),
        ];
        service.getExerciseScoresForCourse(1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });
});
