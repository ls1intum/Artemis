import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { Course } from 'app/course/shared/entities/course.model';
import { ComponentRef } from '@angular/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, RouterLink, RouterModule } from '@angular/router';
import { NgClass } from '@angular/common';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiChartTooltipConfig, TumAetUiDoughnutChartComponent } from '@tumaet/ui-angular';

describe('CourseDetailDoughnutChartComponent', () => {
    let fixture: ComponentFixture<CourseDetailDoughnutChartComponent>;
    let component: CourseDetailDoughnutChartComponent;
    let componentRef: ComponentRef<CourseDetailDoughnutChartComponent>;

    const course = { id: 1, isAtLeastInstructor: true } as Course;
    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CourseDetailDoughnutChartComponent, RouterModule.forRoot([])],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).overrideComponent(CourseDetailDoughnutChartComponent, {
            set: {
                imports: [RouterLink, NgClass, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), TumAetUiDoughnutChartComponent],
            },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseDetailDoughnutChartComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    beforeEach(() => {
        // JSDOM has no layout; the real chart only renders slices after measuring a positive size.
        vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
            width: 300,
            height: 300,
            top: 0,
            left: 0,
            right: 300,
            bottom: 300,
            x: 0,
            y: 0,
            toJSON: () => ({}),
        } as DOMRect);
        componentRef.setInput('course', course);
        componentRef.setInput('contentType', DoughnutChartType.ASSESSMENT);
        componentRef.setInput('currentPercentage', percentage);
        componentRef.setInput('currentAbsolute', absolute);
        componentRef.setInput('currentMax', max);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([DoughnutChartType.CURRENT_LLM_COST, DoughnutChartType.AVERAGE_EXERCISE_SCORE, DoughnutChartType.ASSESSMENT])(
        'only enables chart navigation when a destination exists (%s)',
        (contentType) => {
            componentRef.setInput('contentType', contentType);
            fixture.detectChanges();
            const active = !!component.titleLink();
            const chart = fixture.nativeElement.querySelector('.doughnut-chart-container') as HTMLElement;
            const navigate = vi.spyOn(component, 'openCorrespondingPage').mockImplementation(() => {});
            expect(chart.getAttribute('role')).toBe(active ? 'button' : null);
            expect(chart.tabIndex).toBe(active ? 0 : -1);
            for (const repeat of [false, true]) {
                const event = new KeyboardEvent('keydown', { key: ' ', repeat, bubbles: true, cancelable: true });
                chart.dispatchEvent(event);
                expect(event.defaultPrevented).toBe(active);
            }
            expect(navigate).not.toHaveBeenCalled();
            chart.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
            expect(navigate).toHaveBeenCalledTimes(active ? 1 : 0);
            chart.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
            expect(navigate).toHaveBeenCalledTimes(active ? 2 : 0);
        },
    );

    it('should initialize', () => {
        fixture.detectChanges();

        const expected = [absolute, max - absolute, 0];
        expect(component.stats()).toEqual(expected);
        expect(component.chartEntries()[0].value).toBe(expected[0]);
        expect(component.chartEntries()[1].value).toBe(expected[1]);
        expect(component.chartEntries()[2].value).toBe(expected[2]);

        componentRef.setInput('currentMax', 0);
        fixture.detectChanges();

        // display grey color when currentMax = 0
        expect(component.chartEntries()[0].value).toBe(0);
        expect(component.chartEntries()[1].value).toBe(0);
        expect(component.chartEntries()[2].value).toBe(1);
    });

    it('should set the right title and link', () => {
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('assessments');
        expect(component.titleLink()).toBe('assessment-dashboard');

        componentRef.setInput('contentType', DoughnutChartType.COMPLAINTS);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('complaints');
        expect(component.titleLink()).toBe('complaints');

        componentRef.setInput('contentType', DoughnutChartType.FEEDBACK);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('moreFeedback');
        expect(component.titleLink()).toBe('more-feedback-requests');

        componentRef.setInput('contentType', DoughnutChartType.AVERAGE_COURSE_SCORE);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('averageStudentScore');
        expect(component.titleLink()).toBe('scores');

        componentRef.setInput('contentType', DoughnutChartType.CURRENT_LLM_COST);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('currentTotalLLMCost');
        expect(component.titleLink()).toBeUndefined();

        componentRef.setInput('contentType', DoughnutChartType.AVERAGE_EXERCISE_SCORE);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('');
        expect(component.titleLink()).toBeUndefined();
    });

    it.each([0, 82.3])('keeps the LLM cost visible without a placeholder tooltip (cost: %s)', async (cost) => {
        componentRef.setInput('contentType', DoughnutChartType.CURRENT_LLM_COST);
        componentRef.setInput('currentAbsolute', undefined);
        componentRef.setInput('currentPercentage', undefined);
        componentRef.setInput('currentMax', 0);
        componentRef.setInput('showText', `${cost} €`);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="course-chart-values"]').textContent).toContain(`${cost} €`);
        const placeholder = fixture.nativeElement.querySelectorAll('tumaet-ui-doughnut-chart svg path')[2] as SVGPathElement;
        placeholder.dispatchEvent(new MouseEvent('mouseenter', { clientX: 40, clientY: 40 }));
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('tumaet-ui-chart-tooltip')).toBeNull();
    });

    it.each([
        { absolute: 80, max: 100, slice: 0, expected: '80' },
        { absolute: 0, max: 0, slice: 2, expected: '0' },
    ])('retains assessment tooltips when the maximum is $max', async ({ absolute, max, slice, expected }) => {
        componentRef.setInput('currentAbsolute', absolute);
        componentRef.setInput('currentMax', max);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const path = fixture.nativeElement.querySelectorAll('tumaet-ui-doughnut-chart svg path')[slice] as SVGPathElement;
        path.dispatchEvent(new MouseEvent('mouseenter', { clientX: 40, clientY: 40 }));
        fixture.detectChanges();

        const tooltip = fixture.nativeElement.querySelector('tumaet-ui-chart-tooltip') as HTMLElement;
        expect(tooltip.lastElementChild!.textContent!.trim()).toBe(expected);
    });

    it('should show only the value in the tooltip body', () => {
        fixture.detectChanges();
        const tooltip = component.chartConfig().tooltip as TumAetUiChartTooltipConfig;

        expect(tooltip.label!({ seriesIndex: 0, index: 0, label: 'Done', value: 20 })).toBe('20');
    });
});
