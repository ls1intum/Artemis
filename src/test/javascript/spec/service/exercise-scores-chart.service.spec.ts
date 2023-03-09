import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';
import { SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../test.module';

describe('Exercise Scores Chart Service', () => {
    let service: ExerciseScoresChartService;
    let httpMock: HttpTestingController;
    let elemDefault: ExerciseScoresDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
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
