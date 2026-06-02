import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { DoughnutChartComponent } from 'app/exercise/statistics/doughnut-chart/doughnut-chart.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('DoughnutChartComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<DoughnutChartComponent>;
    let component: DoughnutChartComponent;
    let router: Router;

    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [DoughnutChartComponent],
            providers: [provideRouter([])],
        })
            .overrideTemplate(DoughnutChartComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(DoughnutChartComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);

        fixture.componentRef.setInput('course', { id: 1 });
        fixture.componentRef.setInput('exerciseId', 2);
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        fixture.componentRef.setInput('currentPercentage', percentage);
        fixture.componentRef.setInput('currentAbsolute', absolute);
        fixture.componentRef.setInput('currentMax', max);
    });

    it('should initialize', () => {
        fixture.componentRef.setInput('contentType', DoughnutChartType.AVERAGE_EXERCISE_SCORE);
        component.ngOnChanges();
        const expected = [absolute, max - absolute, 0];
        expect(component.stats).toEqual(expected);
        expect(component.ngxDoughnutData[0].value).toBe(expected[0]);
        expect(component.ngxDoughnutData[1].value).toBe(expected[1]);
        expect(component.ngxDoughnutData[2].value).toBe(expected[2]);

        fixture.componentRef.setInput('currentMax', 0);
        component.ngOnChanges();

        expect(component.ngxDoughnutData[0].value).toBe(0);
        expect(component.ngxDoughnutData[1].value).toBe(0);
        expect(component.ngxDoughnutData[2].value).toBe(1);
    });

    it('should use fallback value when currentAbsolute is undefined and stats are not received', () => {
        fixture.componentRef.setInput('currentAbsolute', undefined);
        component.receivedStats = false;

        component.ngOnChanges();

        expect(component.ngxDoughnutData[0].value).toBe(0);
        expect(component.ngxDoughnutData[1].value).toBe(0);
        expect(component.ngxDoughnutData[2].value).toBe(1);
    });

    describe('setting titles for different chart types', () => {
        it('should set title for average exercise score', () => {
            fixture.componentRef.setInput('contentType', DoughnutChartType.AVERAGE_EXERCISE_SCORE);

            component.ngOnInit();

            expect(component.doughnutChartTitle).toBe('averageScore');
            expect(component.titleLink).toEqual(['/course-management/1/text-exercises/2/scores']);
        });

        it('should set title for participations', () => {
            fixture.componentRef.setInput('contentType', DoughnutChartType.PARTICIPATIONS);

            component.ngOnInit();

            expect(component.doughnutChartTitle).toBe('participationRate');
            expect(component.titleLink).toEqual(['/course-management/1/text-exercises/2/participations']);
        });

        it('should set title for question chart', () => {
            fixture.componentRef.setInput('contentType', DoughnutChartType.QUESTIONS);

            component.ngOnInit();

            expect(component.doughnutChartTitle).toBe('resolved_posts');
            expect(component.titleLink).toEqual(['/courses/1/exercises/2']);
        });
    });

    it('should open corresponding page', () => {
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        fixture.componentRef.setInput('contentType', DoughnutChartType.AVERAGE_EXERCISE_SCORE);
        component.ngOnInit();

        component.openCorrespondingPage();

        expect(navigateSpy).toHaveBeenCalledTimes(1);
    });
});
