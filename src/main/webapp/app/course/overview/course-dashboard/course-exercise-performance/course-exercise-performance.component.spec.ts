import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { ChartModule, UIChart } from 'primeng/chart';
import { TooltipItem } from 'chart.js';
import { CourseExercisePerformanceComponent, ExercisePerformance } from 'app/course/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';

describe('CourseExercisePerformanceComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseExercisePerformanceComponent>;
    let component: CourseExercisePerformanceComponent;
    let componentRef: ComponentRef<CourseExercisePerformanceComponent>;

    const exercisePerformance: ExercisePerformance[] = [
        { exerciseId: 1, title: 'First Exercise', shortName: 'first', score: 33.333, averageScore: 50.5 },
        { exerciseId: 2, title: 'Second Exercise', score: 80, averageScore: 60.6 },
    ];

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).overrideComponent(CourseExercisePerformanceComponent, {
            remove: { imports: [ChartModule] },
            add: { imports: [MockComponent(UIChart)] },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseExercisePerformanceComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should signal that no data is available without performances', () => {
        componentRef.setInput('exercisePerformance', []);
        fixture.detectChanges();

        expect(component.isDataAvailable()).toBe(false);
    });

    it('should convert the performances into chart data', () => {
        componentRef.setInput('exercisePerformance', exercisePerformance);
        fixture.detectChanges();

        expect(component.isDataAvailable()).toBe(true);

        const chartData = component.chartData();
        // the x-axis shows the upper cased short name (fallback: title)
        expect(chartData.labels).toEqual(['FIRST', 'Second Exercise']);
        expect(chartData.datasets).toHaveLength(2);
        expect(chartData.datasets[0].label).toBe('artemisApp.courseStudentDashboard.exercisePerformance.yourScoreLabel');
        expect(chartData.datasets[0].data).toEqual([33.3, 80]);
        expect(chartData.datasets[1].label).toBe('artemisApp.courseStudentDashboard.exercisePerformance.averageScoreLabel');
        expect(chartData.datasets[1].data).toEqual([50.5, 60.6]);
        // the exercise title is attached as metadata for the tooltip
        expect(chartData.datasets[0].meta?.[0]?.['title']).toBe('First Exercise');
    });

    it('should compute the y-axis maximum from the scores', () => {
        componentRef.setInput('exercisePerformance', exercisePerformance);
        fixture.detectChanges();

        // all scores are below 100, so the axis is capped at 100
        expect(component.yScaleMax()).toBe(100);

        componentRef.setInput('exercisePerformance', [{ exerciseId: 3, title: 'Bonus Exercise', score: 133.7, averageScore: 50 }]);
        fixture.detectChanges();

        expect(component.yScaleMax()).toBe(140);
        expect((component.chartOptions().scales?.y as { max?: number }).max).toBe(140);
    });

    it('should show the exercise title and score percentages in the tooltip', () => {
        componentRef.setInput('exercisePerformance', exercisePerformance);
        fixture.detectChanges();

        const callbacks = component.chartOptions().plugins?.tooltip?.callbacks as {
            title: (items: TooltipItem<'line'>[]) => string;
            label: (item: TooltipItem<'line'>) => string;
        };
        const item = { dataset: component.chartData().datasets[0], dataIndex: 1, parsed: { y: 80 } } as TooltipItem<'line'>;

        expect(callbacks.title([item])).toBe('Second Exercise');
        expect(callbacks.label(item)).toBe('artemisApp.courseStudentDashboard.exercisePerformance.yourScoreLabel: 80%');
    });
});
