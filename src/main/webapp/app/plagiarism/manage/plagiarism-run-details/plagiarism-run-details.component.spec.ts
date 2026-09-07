import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { Range } from 'app/foundation/util/utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismRunDetailsComponent } from 'app/plagiarism/manage/plagiarism-run-details/plagiarism-run-details.component';
import { PlagiarismInspectorService } from 'app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.service';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { DatePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiChartSelectEvent, TumUiChartTooltipConfig } from '@tumaet/ui-angular';

describe('Plagiarism Run Details', () => {
    let comp: PlagiarismRunDetailsComponent;
    let fixture: ComponentFixture<PlagiarismRunDetailsComponent>;

    let injectorService: PlagiarismInspectorService;

    const plagiarismResult = {
        duration: 5200,
        similarityDistribution: [24, 18, 16, 13, 7, 9, 5, 4, 0, 1],
    } as any;

    beforeEach(() => {
        TestBed.overrideComponent(PlagiarismRunDetailsComponent, {
            set: {
                imports: [DatePipe, ArtemisDatePipe, ArtemisTranslatePipe],
                schemas: [CUSTOM_ELEMENTS_SCHEMA],
            },
        });
        TestBed.configureTestingModule({
            schemas: [NO_ERRORS_SCHEMA],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismRunDetailsComponent);
        comp = fixture.componentInstance;

        injectorService = TestBed.inject(PlagiarismInspectorService);
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    it('updates chart data on changes', () => {
        vi.spyOn(comp, 'updateChartDataSet');
        vi.spyOn(injectorService, 'filterComparisons').mockReturnValue([]);

        // The constructor effect rebuilds the chart whenever plagiarismResult() changes (replaces the former ngOnChanges).
        fixture.componentRef.setInput('plagiarismResult', plagiarismResult);
        fixture.detectChanges();

        expect(comp.updateChartDataSet).toHaveBeenCalledOnce();
        for (let i = 0; i < 10; i++) {
            expect(comp.chartEntries()[i].value).toBe(plagiarismResult.similarityDistribution[i]);
        }
    });

    it('updates the chart data correctly', () => {
        expect(comp.chartEntries()).toHaveLength(0);

        comp.updateChartDataSet([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);

        expect(comp.chartEntries()).toHaveLength(10);
    });

    it('sets BucketDTOs', () => {
        const filterComparisonsMock = vi.spyOn(injectorService, 'filterComparisons').mockReturnValue([]);

        // The constructor effect rebuilds the buckets whenever plagiarismResult() changes (replaces the former ngOnChanges).
        fixture.componentRef.setInput('plagiarismResult', plagiarismResult);
        fixture.detectChanges();

        expect(filterComparisonsMock).toHaveBeenCalledTimes(10);
        expect(comp.bucketDTOs).toHaveLength(10);
    });

    it.each([0, 10, 20, 30, 40, 50, 60, 70, 80, 90])('emits the correct range if bar is selected', (minimumBorder: number) => {
        const similaritySelectedStub = vi.spyOn(comp.similaritySelected, 'emit').mockImplementation(() => {});
        const maximumBorder = minimumBorder + 10;

        comp.updateChartDataSet(plagiarismResult.similarityDistribution);
        const event: TumUiChartSelectEvent = { seriesIndex: 0, index: minimumBorder / 10, label: `[${minimumBorder}%-${maximumBorder}%)` };

        comp.onSelect(event);

        expect(similaritySelectedStub).toHaveBeenCalledOnce();
        expect(similaritySelectedStub).toHaveBeenCalledWith(new Range(minimumBorder, maximumBorder));
        vi.restoreAllMocks();
    });

    it('does not emit a range if the click did not hit a bar', () => {
        const similaritySelectedStub = vi.spyOn(comp.similaritySelected, 'emit').mockImplementation(() => {});

        comp.updateChartDataSet(plagiarismResult.similarityDistribution);
        comp.onSelect({ seriesIndex: 0, index: 0 });

        expect(similaritySelectedStub).not.toHaveBeenCalled();
    });

    it.each([1, 2, 3])('return correct bucketDTO', (label: number) => {
        comp.chartLabels = ['1', '2', '3'];
        comp.bucketDTOs = [
            { confirmed: 1, denied: 1, open: 1 },
            { confirmed: 2, denied: 2, open: 2 },
            { confirmed: 3, denied: 3, open: 3 },
        ];

        const result = comp.getBucketDTO(label.toString());

        expect(result.confirmed).toBe(label);
        expect(result.denied).toBe(label);
        expect(result.open).toBe(label);
    });

    it('displays the created date (startedAt) in short format without timezone shift', () => {
        const created = dayjs('2024-09-01T10:15:00.000Z');
        const locale = navigator.language || 'en';
        const expected = created.locale(locale).format(ArtemisDatePipe.format(locale, 'short'));

        fixture.componentRef.setInput('plagiarismResult', { ...plagiarismResult, createdDate: created } as any);
        fixture.detectChanges();

        const startedAtInfo: HTMLElement = fixture.nativeElement.querySelector('.plagiarism-run-details-stats-item:nth-child(5) .plagiarism-run-details-info');
        expect(startedAtInfo.textContent?.trim()).toBe(expected);
    });

    it('formats duration as HH:mm:ss in GMT', () => {
        const durationMs = 5200; // 5.2 seconds
        fixture.componentRef.setInput('plagiarismResult', { ...plagiarismResult, duration: durationMs } as any);
        fixture.detectChanges();

        const expected = new Date(durationMs).toISOString().substring(11, 19);

        const durationInfo: HTMLElement = fixture.nativeElement.querySelector('.plagiarism-run-details-stats-item:nth-child(4) .plagiarism-run-details-info.duration');
        expect(durationInfo.textContent?.trim()).toBe(expected);
    });

    it('shows empty created date when not provided', () => {
        fixture.componentRef.setInput('plagiarismResult', { ...plagiarismResult, createdDate: undefined } as any);
        fixture.detectChanges();

        const startedAtInfo: HTMLElement = fixture.nativeElement.querySelector('.plagiarism-run-details-stats-item:nth-child(5) .plagiarism-run-details-info');
        expect(startedAtInfo.textContent?.trim()).toBe('');
    });

    it('does not rebuild the chart while no plagiarism result is set', () => {
        const updateSpy = vi.spyOn(comp, 'updateChartDataSet');

        // The effect runs on change detection but must skip its work while plagiarismResult() is undefined.
        fixture.detectChanges();

        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('falls back to empty arrays when the result has neither comparisons nor a similarity distribution', () => {
        const filterComparisonsMock = vi.spyOn(injectorService, 'filterComparisons').mockReturnValue([]);

        fixture.componentRef.setInput('plagiarismResult', { duration: 0 } as any);
        fixture.detectChanges();

        expect(filterComparisonsMock).toHaveBeenCalledTimes(10);
        expect(comp.bucketDTOs).toHaveLength(10);
        expect(comp.chartEntries()).toHaveLength(0);
    });

    it('builds the tooltip title and label lines via the chart options callbacks', () => {
        vi.spyOn(injectorService, 'filterComparisons').mockReturnValue([]);
        fixture.componentRef.setInput('plagiarismResult', plagiarismResult);
        fixture.detectChanges();

        const tooltip = comp.chartConfig().tooltip as TumUiChartTooltipConfig;

        // title(): empty string when nothing is hovered, otherwise a (translated) string.
        expect(tooltip.title!([])).toBe('');
        expect(typeof tooltip.title!([{ seriesIndex: 0, index: 0, label: '[0%-10%)', value: 5 }])).toBe('string');

        // label(): a known bucket label with data present produces the five detail lines.
        const knownLabelLines = tooltip.label!({ seriesIndex: 0, index: 0, label: '[0%-10%)', value: 24 });
        expect(knownLabelLines).toHaveLength(5);

        // label(): an unknown label hits the bucketDTO?.x ?? 0 fallback.
        const fallbackLines = tooltip.label!({ seriesIndex: 0, index: 0, label: 'unknown', value: 0 });
        expect(fallbackLines).toHaveLength(5);
    });

    it('reports a zero portion in the tooltip label when no plagiarisms were detected', () => {
        vi.spyOn(injectorService, 'filterComparisons').mockReturnValue([]);
        // An all-zero distribution keeps totalDetectedPlagiarisms at 0, exercising the "> 0 ? … : 0" branch.
        fixture.componentRef.setInput('plagiarismResult', { duration: 0, similarityDistribution: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] } as any);
        fixture.detectChanges();

        const tooltip = comp.chartConfig().tooltip as TumUiChartTooltipConfig;
        const lines = tooltip.label!({ seriesIndex: 0, index: 0, label: '[0%-10%)', value: 0 });

        expect(lines).toHaveLength(5);
    });
});
