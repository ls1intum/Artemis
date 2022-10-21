import { RouterTestingModule } from '@angular/router/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Course } from 'app/entities/course.model';
import { PieChartModule } from '@swimlane/ngx-charts';

describe('CourseDetailDoughnutChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailDoughnutChartComponent>;
    let component: CourseDetailDoughnutChartComponent;

    const course = { id: 1, isAtLeastInstructor: true } as Course;
    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(PieChartModule)],
            declarations: [CourseDetailDoughnutChartComponent, MockPipe(ArtemisTranslatePipe)],
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
        expect(component.stats).toEqual(expected);
        expect(component.ngxData[0].value).toBe(expected[0]);
        expect(component.ngxData[1].value).toBe(expected[1]);

        component.currentMax = 0;
        component.ngOnChanges();

        expect(component.ngxData[0].value).toBe(1);
        expect(component.ngxData[1].value).toBe(0);
    });

    it('should set the right title and link', () => {
        component.ngOnInit();
        expect(component.doughnutChartTitle).toBe('assessments');
        expect(component.titleLink).toBe('assessment-dashboard');

        component.contentType = DoughnutChartType.COMPLAINTS;

        component.ngOnInit();
        expect(component.doughnutChartTitle).toBe('complaints');
        expect(component.titleLink).toBe('complaints');

        component.contentType = DoughnutChartType.FEEDBACK;

        component.ngOnInit();

        expect(component.doughnutChartTitle).toBe('moreFeedback');
        expect(component.titleLink).toBe('more-feedback-requests');

        component.contentType = DoughnutChartType.AVERAGE_COURSE_SCORE;

        component.ngOnInit();

        expect(component.doughnutChartTitle).toBe('averageStudentScore');
        expect(component.titleLink).toBe('scores');

        component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;

        component.ngOnInit();

        expect(component.doughnutChartTitle).toBe('');
        expect(component.titleLink).toBeUndefined();
    });
});
