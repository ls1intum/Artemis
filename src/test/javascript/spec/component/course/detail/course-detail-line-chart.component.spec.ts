import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockModule, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { NgxChartsModule } from '@swimlane/ngx-charts';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseDetailLineChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailLineChartComponent>;
    let component: CourseDetailLineChartComponent;
    let service: CourseManagementService;

    const initialStats = [26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12, 26, 46, 34, 12];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(NgxChartsModule)],
            declarations: [CourseDetailLineChartComponent,
                MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseDetailLineChartComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
            });
    });

    beforeEach(() => {
        component.course = { id: 1 };
        component.numberOfStudentsInCourse = 50;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        const graphData: number[] = [];
        const spy = jest.spyOn(service, 'getStatisticsData');
        for (let i = 0; i < 16; i++) {
            graphData[i] = 40 + 2 * i;
        }
        spy.mockReturnValue(of(graphData));

        component.initialStats = initialStats;

        component.ngOnChanges();

        for (let i = 0; i < 16; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).to.equal(initialStats[i]);
        }
        expect(component.absoluteSeries.length).to.equal(initialStats.length);

        component.switchTimeSpan(true);

        for (let i = 0; i < 16; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).to.equal(graphData[i]);
        }
        expect(component.data[0].series.length).to.equal(initialStats.length);

        component.numberOfStudentsInCourse = 0;
        component.switchTimeSpan(true);

        expect(component.data[0].series.length).to.equal(initialStats.length);
        for (let i = 0; i < 16; i++) {
            expect(component.absoluteSeries[i]['absoluteValue']).to.equal(0);
        }
    });
});
