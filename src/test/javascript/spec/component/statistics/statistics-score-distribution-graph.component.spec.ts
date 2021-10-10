import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ChartsModule } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { ChartData } from 'chart.js';

chai.use(sinonChai);
const expect = chai.expect;

describe('StatisticsScoreDistributionGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsScoreDistributionGraphComponent>;
    let component: StatisticsScoreDistributionGraphComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ChartsModule],
            declarations: [StatisticsScoreDistributionGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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
        expect(component.barChartLabels).to.deep.equal(expectedLabels);
        let expectedRelativeData = [0, 0, 0, 0, 0, 50, 0, 0, 0, 50];
        expect(component.chartData[0].data).to.deep.equal(expectedRelativeData);

        component.numberOfExerciseScores = 0;
        component.ngOnInit();
        expectedRelativeData = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        expect(component.chartData[0].data).to.deep.equal(expectedRelativeData);
    });

    it('tests data functions', () => {
        // we need a @ts-ignore so we can execute the nested functions
        // @ts-ignore
        expect(component!.barChartOptions!.tooltips!.callbacks!.label({ index: 5 }, {} as ChartData)!).to.be.equal(' 5');
        component.scoreDistribution = undefined;
        // @ts-ignore
        expect(component.barChartOptions!.tooltips!.callbacks!.label({ index: 5 }, {} as ChartData)).to.be.equal(' 0');
    });
});
