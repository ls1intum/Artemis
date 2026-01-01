import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailDoughnutChartComponent } from 'app/core/course/manage/detail/course-detail-doughnut-chart.component';
import { DoughnutChartType } from 'app/core/course/manage/detail/course-detail.component';
import { Course } from 'app/core/course/shared/entities/course.model';

describe('CourseDetailDoughnutChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailDoughnutChartComponent>;
    let component: CourseDetailDoughnutChartComponent;

    const course = { id: 1, isAtLeastInstructor: true } as Course;
    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();
        fixture = TestBed.createComponent(CourseDetailDoughnutChartComponent);
        component = fixture.componentInstance;
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
        const expected = [absolute, max - absolute, 0];
        expect(component.stats).toEqual(expected);
        expect(component.ngxData[0].value).toBe(expected[0]);
        expect(component.ngxData[1].value).toBe(expected[1]);
        expect(component.ngxData[2].value).toBe(expected[2]);

        component.currentMax = 0;
        component.ngOnChanges();

        // display grey color when currentMax = 0
        expect(component.ngxData[0].value).toBe(0);
        expect(component.ngxData[1].value).toBe(0);
        expect(component.ngxData[2].value).toBe(1);
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

        component.contentType = DoughnutChartType.CURRENT_LLM_COST;

        component.ngOnInit();

        expect(component.doughnutChartTitle).toBe('currentTotalLLMCost');
        expect(component.titleLink).toBeUndefined();

        component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;

        component.ngOnInit();

        expect(component.doughnutChartTitle).toBe('');
        expect(component.titleLink).toBeUndefined();
    });
});
