import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SimpleChange } from '@angular/core';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { Graphs, SpanType, StatisticsView } from 'app/exercise/shared/entities/statistics.model';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';

describe('StatisticsGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsGraphComponent>;
    let component: StatisticsGraphComponent;
    let service: StatisticsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting(), provideNoopAnimationsForTests()],
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
                    arrayLength = dayjs().diff(dayjs().subtract(1, 'months'), 'days');
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
