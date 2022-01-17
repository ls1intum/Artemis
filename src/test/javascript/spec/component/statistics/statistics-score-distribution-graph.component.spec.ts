import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { BarChartModule } from '@swimlane/ngx-charts';

describe('StatisticsScoreDistributionGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsScoreDistributionGraphComponent>;
    let component: StatisticsScoreDistributionGraphComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule)],
            declarations: [StatisticsScoreDistributionGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsScoreDistributionGraphComponent);
                component = fixture.componentInstance;
                component.averageScoreOfExercise = 75;
                component.scoreDistribution = [0, 0, 0, 0, 0, 5, 0, 0, 0, 5];
                component.numberOfExerciseScores = 10;
                fixture.detectChanges();
            });
    });

    it('should initialize', () => {
        const expectedLabels = ['[0, 10)', '[10, 20)', '[20, 30)', '[30, 40)', '[40, 50)', '[50, 60)', '[60, 70)', '[70, 80)', '[80, 90)', '[90, 100]'];
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
});
