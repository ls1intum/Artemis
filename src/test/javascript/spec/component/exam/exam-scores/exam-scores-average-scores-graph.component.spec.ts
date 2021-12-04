import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ExamScoresAverageScoresGraphComponent } from 'app/exam/exam-scores/exam-scores-average-scores-graph.component';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { AggregatedExerciseGroupResult, AggregatedExerciseResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('ExamScoresAverageScoresGraphComponent', () => {
    let fixture: ComponentFixture<ExamScoresAverageScoresGraphComponent>;
    let component: ExamScoresAverageScoresGraphComponent;

    const returnValue = {
        exerciseGroupId: 1,
        title: 'Patterns',
        averagePoints: 5,
        averagePercentage: 50,
        maxPoints: 10,
        exerciseResults: [
            {
                exerciseId: 2,
                title: 'StrategyPattern',
                maxPoints: 10,
                averagePoints: 6,
                averagePercentage: 60,
            } as AggregatedExerciseResult,
            {
                exerciseId: 3,
                title: 'BridgePattern',
                maxPoints: 10,
                averagePoints: 4,
                averagePercentage: 40,
            } as AggregatedExerciseResult,
        ],
    } as AggregatedExerciseGroupResult;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(BarChartModule)],
            declarations: [ExamScoresAverageScoresGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(CourseManagementService, {
                    find: () => {
                        return of(new HttpResponse({ body: { accuracyOfScores: 1 } }));
                    },
                }),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamScoresAverageScoresGraphComponent);
                component = fixture.componentInstance;

                component.averageScores = returnValue;
                fixture.detectChanges();
            });
    });

    it('should initialize', () => {
        const expectedData = [
            { name: 'Patterns', value: 50 },
            { name: '2 StrategyPattern', value: 60 },
            { name: '3 BridgePattern', value: 40 },
        ];

        expect(component.ngxData).toEqual(expectedData);
    });
});
