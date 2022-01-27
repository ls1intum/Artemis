import { HttpTestingController } from '@angular/common/http/testing';
import { SimpleChange } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BarChartModule } from '@swimlane/ngx-charts';

describe('StatisticsGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsGraphComponent>;
    let component: StatisticsGraphComponent;
    let service: StatisticsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(BarChartModule)],
            declarations: [StatisticsGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsGraphComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(StatisticsService);
                httpMock = TestBed.inject(HttpTestingController);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        let graphData: number[] = [];
        component.graphType = Graphs.SUBMISSIONS;
        component.statisticsView = StatisticsView.ARTEMIS;
        let arrayLength = 0;
        const getChartDataMock = jest.spyOn(service, 'getChartData');

        for (const span of Object.values(SpanType)) {
            component.currentSpan = span;
            switch (span) {
                case SpanType.DAY:
                    arrayLength = 24;
                    break;
                case SpanType.WEEK:
                    arrayLength = 7;
                    break;
                case SpanType.MONTH:
                    const startDate = dayjs().subtract(1, 'months');
                    arrayLength = dayjs().diff(startDate, 'days');
                    break;
                case SpanType.QUARTER:
                    arrayLength = 12;
                    break;
                case SpanType.YEAR:
                    arrayLength = 12;
                    break;
            }
            graphData = [];
            for (let i = 0; i < arrayLength; i++) {
                graphData[i] = i + 1;
            }
            getChartDataMock.mockReturnValue(of(graphData));

            const changes = { currentSpan: { currentValue: span } as SimpleChange };
            component.ngOnChanges(changes);

            expect(component.dataForSpanType).toEqual(graphData);
            graphData.forEach((data, index) => {
                expect(component.ngxData[index].value).toBe(data);
            });
            expect(component.currentSpan).toEqual(span);
        }
    });

    it('should initialize after changes', () => {
        component.graphType = Graphs.SUBMISSIONS;
        component.currentSpan = SpanType.WEEK;
        component.statisticsView = StatisticsView.COURSE;
        component.entityId = 1;
        const changes = { currentSpan: { currentValue: SpanType.DAY } as SimpleChange };
        const graphData = [];
        for (let i = 0; i < 24; i++) {
            graphData[i] = i + 1;
        }

        component.ngOnChanges(changes);
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([...graphData]);

        expect(component.dataForSpanType).toEqual(graphData);
        expect(component.currentSpan).toEqual(SpanType.DAY);
    });

    it('should switch time span', () => {
        component.graphType = Graphs.SUBMISSIONS;
        component.currentSpan = SpanType.WEEK;
        component.statisticsView = StatisticsView.ARTEMIS;
        const graphData = [1, 2, 3, 4, 5, 6, 8];
        jest.spyOn(service, 'getChartData').mockReturnValue(of(graphData));

        fixture.detectChanges();

        component.switchTimeSpan(true);

        expect(component.dataForSpanType).toEqual(graphData);
    });
});
