import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockPipe } from 'ng-mocks';
import { ChartsModule } from 'ng2-charts';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseDetailLineChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailLineChartComponent>;
    let component: CourseDetailLineChartComponent;
    let service: CourseManagementService;

    const initialStats = [26, 46, 34, 12];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), ChartsModule],
            declarations: [CourseDetailLineChartComponent, MockPipe(ArtemisTranslatePipe)],
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
        for (let i = 0; i < 4; i++) {
            graphData[i] = 40 + 2 * i;
        }
        spy.mockReturnValue(of(graphData));

        component.ngOnChanges();

        expect(component.data).to.deep.equal([]);

        component.initialStats = initialStats;

        component.ngOnChanges();

        let expectedStats = [52, 92, 68, 24];
        expect(component.data).to.deep.equal(expectedStats);
        expect(component.chartData[0].data).to.deep.equal(expectedStats);

        component.switchTimeSpan(true);

        expectedStats = [80, 84, 88, 92];
        expect(component.data).to.deep.equal(expectedStats);
        expect(component.chartData[0].data).to.deep.equal(expectedStats);

        component.numberOfStudentsInCourse = 0;
        component.switchTimeSpan(true);

        expectedStats = [0, 0, 0, 0];
        expect(component.data).to.deep.equal(expectedStats);
        expect(component.chartData[0].data).to.deep.equal(expectedStats);
    });
});
