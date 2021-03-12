import { HttpTestingController } from '@angular/common/http/testing';
import { SimpleChange } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { StatisticsGraphComponent } from 'app/admin/statistics/statistics-graph.component';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { Graphs, SpanType } from 'app/entities/statistics.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import * as chai from 'chai';
import * as moment from 'moment';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ChartsModule } from 'ng2-charts';
import { MomentModule } from 'ngx-moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs/internal/observable/of';
import * as sinonChai from 'sinon-chai';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';

chai.use(sinonChai);
const expect = chai.expect;

describe('StatisticsGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsGraphComponent>;
    let component: StatisticsGraphComponent;
    let service: StatisticsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule, ChartsModule],
            declarations: [StatisticsGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsGraphComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(StatisticsService);
                httpMock = TestBed.get(HttpTestingController);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        let graphData: number[] = [];
        component.graphType = Graphs.SUBMISSIONS;
        let arrayLength = 0;
        const spy = spyOn(service, 'getChartData');

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
                    const startDate = moment().subtract(1, 'months');
                    arrayLength = moment().diff(startDate, 'days');
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
            spy.and.returnValue(of(graphData));

            const changes = { currentSpan: { currentValue: span } as SimpleChange };
            component.ngOnChanges(changes);

            expect(component.dataForSpanType).to.equal(graphData);
            expect(component.chartData[0].data).to.equal(graphData);
            expect(component.currentSpan).to.equal(span);
        }
    });

    it('should initialize after changes', () => {
        component.graphType = Graphs.SUBMISSIONS;
        component.currentSpan = SpanType.WEEK;
        const changes = { currentSpan: { currentValue: SpanType.DAY } as SimpleChange };
        const graphData = [];
        for (let i = 0; i < 24; i++) {
            graphData[i] = i + 1;
        }

        component.ngOnChanges(changes);
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([...graphData]);

        expect(component.dataForSpanType).to.deep.equal(graphData);
        expect(component.currentSpan).to.equal(SpanType.DAY);
    });

    it('should switch time span', () => {
        component.graphType = Graphs.SUBMISSIONS;
        component.currentSpan = SpanType.WEEK;
        const graphData = [1, 2, 3, 4, 5, 6, 8];
        spyOn(service, 'getChartData').and.returnValue(of(graphData));

        fixture.detectChanges();

        component.switchTimeSpan(true);

        expect(component.dataForSpanType).to.equal(graphData);
        expect(component).to.be.ok;
    });
});
