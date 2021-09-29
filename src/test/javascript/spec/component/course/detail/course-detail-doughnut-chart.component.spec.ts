import { RouterTestingModule } from '@angular/router/testing';
import * as chai from 'chai';
import { ChartsModule } from 'ng2-charts';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../../test.module';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Course } from 'app/entities/course.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseDetailDoughnutChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailDoughnutChartComponent>;
    let component: CourseDetailDoughnutChartComponent;

    const course = { id: 1 } as Course;
    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), ChartsModule],
            declarations: [CourseDetailDoughnutChartComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseDetailDoughnutChartComponent);
                component = fixture.componentInstance;
            });
    });

    beforeEach(() => {
        component.course = course;
        component.contentType = DoughnutChartType.ASSESSMENT;
        component.currentPercentage = absolute;
        component.currentAbsolute = percentage;
        component.currentMax = max;
    });

    it('should initialize', () => {
        component.ngOnChanges();
        const expected = [absolute, max - absolute];
        expect(component.stats).to.deep.equal(expected);
        expect(component.doughnutChartData[0].data).to.deep.equal(expected);

        component.currentMax = 0;
        component.ngOnChanges();
        expect(component.doughnutChartData[0].data).to.deep.equal([-1, 0]);
    });
});
