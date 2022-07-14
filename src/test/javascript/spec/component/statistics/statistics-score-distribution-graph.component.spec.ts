import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ExerciseType } from 'app/entities/exercise.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

describe('StatisticsScoreDistributionGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsScoreDistributionGraphComponent>;
    let component: StatisticsScoreDistributionGraphComponent;
    let routeInNewTabStub: jest.SpyInstance;

    const expectedLabels = ['[0, 10)', '[10, 20)', '[20, 30)', '[30, 40)', '[40, 50)', '[50, 60)', '[60, 70)', '[70, 80)', '[80, 90)', '[90, 100]'];
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule)],
            declarations: [StatisticsScoreDistributionGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisNavigationUtilService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsScoreDistributionGraphComponent);
                component = fixture.componentInstance;
                component.averageScoreOfExercise = 75;
                component.scoreDistribution = [0, 0, 0, 0, 0, 5, 0, 0, 0, 5];
                component.numberOfExerciseScores = 10;
                component.exerciseId = 1;
                component.courseId = 2;
                component.exerciseType = ExerciseType.FILE_UPLOAD;

                const navigationService = TestBed.inject(ArtemisNavigationUtilService);
                routeInNewTabStub = jest.spyOn(navigationService, 'routeInNewTab').mockImplementation();
                fixture.detectChanges();
            });
    });

    it('should initialize', () => {
        expect(component.barChartLabels).toEqual(expectedLabels);
        let expectedRelativeData = [0, 0, 0, 0, 0, 50, 0, 0, 0, 50];
        expectedRelativeData.forEach((data, index) => {
            expect(component.ngxData[index].value).toBe(data);
        });

        component.numberOfExerciseScores = 0;
        component.ngOnInit();
        expectedRelativeData = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        expectedRelativeData.forEach((data, index) => {
            expect(component.ngxData[index].value).toBe(data);
        });
    });

    it.each(expectedLabels)('should delegate the on bar select', (label: string) => {
        const event = { name: label };
        component.ngOnInit();

        component.selectChartBar(event);

        expect(routeInNewTabStub).toHaveBeenCalledOnce();
        expect(routeInNewTabStub).toHaveBeenCalledWith([`/course-management/2/file-upload-exercises/1/scores`], {
            queryParams: { scoreRangeFilter: expectedLabels.indexOf(label) },
        });
    });
});
