import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { ChartModule, UIChart } from 'primeng/chart';
import { TooltipItem } from 'chart.js';
import { CourseExerciseLatenessComponent, ExerciseLateness } from 'app/course/overview/course-dashboard/course-exercise-lateness/course-exercise-lateness.component';

describe('CourseExerciseLatenessComponent', () => {
    let fixture: ComponentFixture<CourseExerciseLatenessComponent>;
    let component: CourseExerciseLatenessComponent;
    let componentRef: ComponentRef<CourseExerciseLatenessComponent>;

    const exerciseLateness: ExerciseLateness[] = [
        { exerciseId: 1, title: 'First Exercise', shortName: 'first', relativeLatestSubmission: 33.333, relativeAverageLatestSubmission: 50.5 },
        { exerciseId: 2, title: 'Second Exercise', relativeLatestSubmission: 80, relativeAverageLatestSubmission: 60.6 },
    ];

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).overrideComponent(CourseExerciseLatenessComponent, {
            remove: { imports: [ChartModule] },
            add: { imports: [MockComponent(UIChart)] },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseExerciseLatenessComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should signal that no data is available without lateness information', () => {
        componentRef.setInput('exerciseLateness', []);
        fixture.detectChanges();

        expect(component.isDataAvailable()).toBe(false);
    });

    it('should convert the lateness information into chart data', () => {
        componentRef.setInput('exerciseLateness', exerciseLateness);
        fixture.detectChanges();

        expect(component.isDataAvailable()).toBe(true);

        const chartData = component.chartData();
        // the x-axis shows the upper cased short name (fallback: title)
        expect(chartData.labels).toEqual(['FIRST', 'Second Exercise']);
        expect(chartData.datasets).toHaveLength(2);
        expect(chartData.datasets[0].label).toBe('artemisApp.courseStudentDashboard.exerciseLateness.yourLatenessLabel');
        expect(chartData.datasets[0].data).toEqual([33.3, 80]);
        expect(chartData.datasets[1].label).toBe('artemisApp.courseStudentDashboard.exerciseLateness.averageLatenessLabel');
        expect(chartData.datasets[1].data).toEqual([50.5, 60.6]);
        // the exercise title is attached as metadata for the tooltip
        expect(chartData.datasets[0].meta?.[0]?.['title']).toBe('First Exercise');
    });

    it('should fall back to 100% for missing relative submission times', () => {
        componentRef.setInput('exerciseLateness', [{ exerciseId: 3, title: 'Third Exercise' }]);
        fixture.detectChanges();

        expect(component.chartData().datasets[0].data).toEqual([100]);
        expect(component.chartData().datasets[1].data).toEqual([100]);
    });

    it('should compute the y-axis maximum from the relative submission times', () => {
        componentRef.setInput('exerciseLateness', exerciseLateness);
        fixture.detectChanges();

        // all values are below 100, so the axis is capped at 100
        expect(component.yScaleMax()).toBe(100);

        componentRef.setInput('exerciseLateness', [{ exerciseId: 3, title: 'Third Exercise', relativeLatestSubmission: 133.7, relativeAverageLatestSubmission: 50 }]);
        fixture.detectChanges();

        expect(component.yScaleMax()).toBe(140);
        expect((component.chartOptions().scales?.y as { max?: number }).max).toBe(140);
    });

    it('should show the exercise title and relative submission times in the tooltip', () => {
        componentRef.setInput('exerciseLateness', exerciseLateness);
        fixture.detectChanges();

        const callbacks = component.chartOptions().plugins?.tooltip?.callbacks as {
            title: (items: TooltipItem<'line'>[]) => string;
            label: (item: TooltipItem<'line'>) => string;
        };
        const item = { dataset: component.chartData().datasets[0], dataIndex: 1, parsed: { y: 80 } } as TooltipItem<'line'>;

        expect(callbacks.title([item])).toBe('Second Exercise');
        expect(callbacks.label(item)).toBe('artemisApp.courseStudentDashboard.exerciseLateness.yourLatenessLabel: 80%');
    });
});
