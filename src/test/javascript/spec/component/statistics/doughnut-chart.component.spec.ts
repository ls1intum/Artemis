import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { DoughnutChartComponent } from 'app/exercises/shared/statistics/doughnut-chart.component';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseType } from 'app/entities/exercise.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { PieChartModule } from '@swimlane/ngx-charts';

describe('DoughnutChartComponent', () => {
    let fixture: ComponentFixture<DoughnutChartComponent>;
    let component: DoughnutChartComponent;
    let router: Router;

    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(PieChartModule)],
            declarations: [DoughnutChartComponent, MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective],
            providers: [{ provide: Router, useClass: MockRouter }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DoughnutChartComponent);
                component = fixture.componentInstance;
                router = TestBed.inject(Router);
            });
    });

    beforeEach(() => {
        component.course = { id: 1 };
        component.exerciseId = 2;
        component.exerciseType = ExerciseType.TEXT;
        component.currentPercentage = absolute;
        component.currentAbsolute = percentage;
        component.currentMax = max;
    });

    it('should initialize', () => {
        component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;
        component.ngOnChanges();
        const expected = [absolute, max - absolute, 0];
        expect(component.stats).toEqual(expected);
        expect(component.ngxDoughnutData[0].value).toBe(expected[0]);
        expect(component.ngxDoughnutData[1].value).toBe(expected[1]);
        expect(component.ngxDoughnutData[2].value).toBe(expected[2]);

        component.currentMax = 0;
        component.ngOnChanges();

        // should show grey color if currentMax = 0
        expect(component.ngxDoughnutData[0].value).toBe(0);
        expect(component.ngxDoughnutData[1].value).toBe(0);
        expect(component.ngxDoughnutData[2].value).toBe(1);
    });
    describe('setting titles for different chart types', () => {
        it('should set title for average exercise score', () => {
            component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;
            component.ngOnInit();
            expect(component.doughnutChartTitle).toBe('averageScore');
            expect(component.titleLink).toEqual([`/course-management/${component.course.id}/${component.exerciseType}-exercises/${component.exerciseId}/scores`]);
        });

        it('should set title for participations', () => {
            component.contentType = DoughnutChartType.PARTICIPATIONS;
            component.ngOnInit();
            expect(component.doughnutChartTitle).toBe('participationRate');
            expect(component.titleLink).toEqual([`/course-management/${component.course.id}/${component.exerciseType}-exercises/${component.exerciseId}/participations`]);
        });

        it('should set title for question chart', () => {
            component.contentType = DoughnutChartType.QUESTIONS;
            component.ngOnInit();
            expect(component.doughnutChartTitle).toBe('resolved_posts');
            expect(component.titleLink).toEqual([`/courses/${component.course.id}/exercises/${component.exerciseId}`]);
        });
    });

    it('should open corresponding page', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;
        component.ngOnInit();
        component.openCorrespondingPage();

        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
