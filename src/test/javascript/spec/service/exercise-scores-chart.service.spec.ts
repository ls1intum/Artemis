import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ArtemisTestModule } from '../test.module';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';
import { provideHttpClient } from '@angular/common/http';

describe('Exercise Scores Chart Service', () => {
    let service: ExerciseScoresChartService;
    let httpMock: HttpTestingController;
    let elemDefault: ExerciseScoresDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(ExerciseScoresChartService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new ExerciseScoresDTO();
    });

    afterEach(() => {
        httpMock.verify();
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
        req.flush(JSON.stringify(returnedFromService));
    });
});
