import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiBarChartComponent, TumAetUiChartDatumContext, TumAetUiChartTooltipConfig } from '@tumaet/ui-angular';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { RatingDistributionComponent } from 'app/metis/manage/dashboard/rating-distribution/rating-distribution.component';
import { MetisCheckStatus, MetisDashboardRowDTO, MetisQuestionDTO } from 'app/metis/shared/entities/metis.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const ALL_STUDENTS = 'artemisApp.metis.dashboard.distribution.allStudents';

function question(id: number, checkId: number, questionOrder: number, focusFilePath: string, llmScore?: number, parentQuestionId?: number): MetisQuestionDTO {
    return {
        id,
        checkId,
        questionOrder,
        focusFilePath,
        focusFileChangedLines: 12,
        questionText: `Question ${id}`,
        timeLimitSeconds: 240,
        regenerationCount: 0,
        llmScore,
        parentQuestionId,
    };
}

function row(participationId: number, tutorialGroupTitle: string, status: MetisCheckStatus, questions: MetisQuestionDTO[]): MetisDashboardRowDTO {
    return {
        participationId,
        studentName: `Student ${participationId}`,
        studentLogin: `student${participationId}`,
        tutorialGroupTitle,
        check: { id: participationId * 10, participationId, status, completedAt: status === MetisCheckStatus.COMPLETED ? dayjs('2026-11-03T10:16:00') : undefined },
        questions,
    };
}

describe('RatingDistributionComponent', () => {
    let fixture: ComponentFixture<RatingDistributionComponent>;
    let component: RatingDistributionComponent;

    // Anna: main ratings 2, 4, 5 (the follow-up rating 3 must not count); Ben: 3, 3, 5; Clara is still answering.
    const rows: MetisDashboardRowDTO[] = [
        row(1, 'Gruppe 1', MetisCheckStatus.COMPLETED, [
            question(101, 10, 1, 'BubbleSort.java', 2),
            question(102, 10, 2, 'BubbleSort.java', 3, 101),
            question(103, 10, 3, 'SortUtils.java', 4),
            question(104, 10, 4, 'BubbleSortTest.java', 5),
        ]),
        row(2, 'Gruppe 2', MetisCheckStatus.COMPLETED, [
            question(201, 20, 1, 'SortUtils.java', 3),
            question(202, 20, 2, 'BubbleSortTest.java', 3),
            question(203, 20, 3, 'BubbleSort.java', 5),
        ]),
        row(3, 'Gruppe 1', MetisCheckStatus.IN_PROGRESS, [question(301, 30, 1, 'BubbleSort.java', 1), question(302, 30, 2, 'SortUtils.java')]),
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RatingDistributionComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(RatingDistributionComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiBarChartComponent] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key), MockComponent(TumAetUiBarChartComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(RatingDistributionComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('rows', rows);
        fixture.componentRef.setInput('points', 5);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function modeButtons(): HTMLButtonElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="rating-distribution-mode"] button'));
    }

    it('should count only the completed students', () => {
        expect(component['completedCount']()).toBe(2);
        expect(fixture.nativeElement.querySelector('[data-testid="rating-distribution-chart"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="rating-distribution-empty"]')).toBeNull();
    });

    it('should chart all students and every tutorial group with one segment per step of the scale', () => {
        const data = component['chartData']();

        expect(data.labels).toEqual([ALL_STUDENTS, 'Gruppe 1', 'Gruppe 2']);
        // The ends and the middle of the scale are spelled out in the legend; the mocked translation hands back the key.
        expect(data.series.map((series) => series.label)).toEqual([
            'artemisApp.metis.dashboard.distribution.legendLowest',
            '2',
            'artemisApp.metis.dashboard.distribution.legendMiddle',
            '4',
            'artemisApp.metis.dashboard.distribution.legendHighest',
        ]);
        expect(data.series.map((series) => series.color)).toEqual([
            'var(--rating-scale-lowest)',
            'var(--rating-scale-low)',
            'var(--rating-scale-neutral)',
            'var(--rating-scale-high)',
            'var(--rating-scale-highest)',
        ]);
        // All students: ratings 2, 4, 5, 3, 3, 5 -> one sixth 2, two sixths 3, one sixth 4, two sixths 5.
        expect(data.series.map((series) => Math.round(series.data[0] ?? 0))).toEqual([0, 17, 33, 17, 33]);
        // Gruppe 1 is Anna alone: 2, 4, 5.
        expect(data.series.map((series) => Math.round(series.data[1] ?? 0))).toEqual([0, 33, 0, 33, 33]);
    });

    it('should merge the outer values of a seven-point scale into the five steps of the scale', () => {
        const translateService = TestBed.inject(TranslateService);
        const instantSpy = vi.spyOn(translateService, 'instant');
        fixture.componentRef.setInput('points', 7);
        fixture.detectChanges();

        const data = component['chartData']();

        // Seven values, five segments: 1–2 no understanding, 3, 4 partial, 5, 6–7 full understanding.
        expect(data.series).toHaveLength(5);
        expect(data.series.map((series) => series.label)).toEqual([
            'artemisApp.metis.dashboard.distribution.legendLowest',
            '3',
            'artemisApp.metis.dashboard.distribution.legendMiddle',
            '5',
            'artemisApp.metis.dashboard.distribution.legendHighest',
        ]);
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.dashboard.distribution.legendLowest', { value: '1–2' });
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.dashboard.distribution.legendMiddle', { value: '4' });
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.dashboard.distribution.legendHighest', { value: '6–7' });
        expect(data.series.map((series) => series.color)).toEqual([
            'var(--rating-scale-lowest)',
            'var(--rating-scale-low)',
            'var(--rating-scale-neutral)',
            'var(--rating-scale-high)',
            'var(--rating-scale-highest)',
        ]);
        // All students: ratings 2, 4, 5, 3, 3, 5 -> one sixth in 1–2, two sixths on 3, one sixth on 4, two sixths on 5, nothing in 6–7.
        expect(data.series.map((series) => Math.round(series.data[0] ?? 0))).toEqual([17, 33, 17, 33, 0]);
        // Gruppe 1 is Anna alone: 2, 4, 5.
        expect(data.series.map((series) => Math.round(series.data[1] ?? 0))).toEqual([33, 0, 33, 33, 0]);
    });

    it('should leave out a step that no value of the scale lands on', () => {
        fixture.componentRef.setInput('points', 4);
        fixture.detectChanges();

        const data = component['chartData']();

        // A four-point scale has no middle value, so there is no neutral segment.
        expect(data.series.map((series) => series.label)).toEqual([
            'artemisApp.metis.dashboard.distribution.legendLowest',
            '2',
            '3',
            'artemisApp.metis.dashboard.distribution.legendHighest',
        ]);
        expect(data.series.map((series) => series.color)).toEqual([
            'var(--rating-scale-lowest)',
            'var(--rating-scale-low)',
            'var(--rating-scale-high)',
            'var(--rating-scale-highest)',
        ]);
        // Ratings 2, 4, 5, 3, 3, 5: the two ratings of 5 lie beyond the scale and are clamped onto its top value.
        expect(data.series.map((series) => Math.round(series.data[0] ?? 0))).toEqual([0, 17, 33, 50]);
    });

    it('should switch to the focus files when the mode changes', () => {
        const buttons = modeButtons();
        expect(buttons).toHaveLength(2);

        buttons[1].click();
        fixture.detectChanges();

        expect(component['mode']()).toBe('file');
        const data = component['chartData']();
        expect(data.labels).toEqual(['BubbleSort.java', 'BubbleSortTest.java', 'SortUtils.java']);
        // BubbleSort.java: Anna 2 and Ben 5; the follow-up on that file does not count.
        expect(data.series.map((series) => Math.round(series.data[0] ?? 0))).toEqual([0, 50, 0, 0, 50]);
    });

    it('should append the average to the category labels and spell out the count in the tooltip', () => {
        const config = component['chartConfig']();

        expect(config.yAxis?.tickFormatter?.('Gruppe 2')).toBe('Gruppe 2 · artemisApp.metis.dashboard.distribution.avg');
        expect(config.yAxis?.tickFormatter?.('unknown')).toBe('unknown');
        expect(config.xAxis?.tickFormatter?.(50)).toBe('50%');

        const translateService = TestBed.inject(TranslateService);
        const instantSpy = vi.spyOn(translateService, 'instant');
        const context: TumAetUiChartDatumContext = { seriesIndex: 2, index: 0, label: ALL_STUDENTS, seriesLabel: '3 partial', value: 33.3, meta: { name: '3 partial', value: 2 } };
        const tooltip = config.tooltip as TumAetUiChartTooltipConfig;

        expect(tooltip.label?.(context)).toBe('artemisApp.metis.dashboard.distribution.tooltip');
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.dashboard.distribution.tooltip', { label: '3 partial', count: 2, percent: 33 });
        // A datum without metadata still gets a line, with a count of zero.
        expect(component['tooltipLabel']({ seriesIndex: 0, index: 0, label: ALL_STUDENTS, value: 0 })).toBe('artemisApp.metis.dashboard.distribution.tooltip');
        expect(instantSpy).toHaveBeenLastCalledWith('artemisApp.metis.dashboard.distribution.tooltip', { label: '', count: 0, percent: 0 });
    });

    it('should grow with the number of bars', () => {
        expect(component['chartHeight']()).toBe(3 * 36 + 96);

        fixture.componentRef.setInput('rows', [rows[0], rows[2]]);
        fixture.detectChanges();

        expect(component['chartHeight']()).toBe(2 * 36 + 96);
    });

    it('should show a note instead of the chart while nobody has completed the check', () => {
        fixture.componentRef.setInput('rows', [rows[2]]);
        fixture.detectChanges();

        expect(component['completedCount']()).toBe(0);
        expect(fixture.nativeElement.querySelector('[data-testid="rating-distribution-chart"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="rating-distribution-empty"]')).not.toBeNull();
    });
});
