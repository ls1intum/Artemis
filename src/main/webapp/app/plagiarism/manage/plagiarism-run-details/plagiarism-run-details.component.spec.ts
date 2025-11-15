import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA, SimpleChange } from '@angular/core';
import { Range } from 'app/shared/util/utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismRunDetailsComponent } from 'app/plagiarism/manage/plagiarism-run-details/plagiarism-run-details.component';
import { PlagiarismInspectorService } from 'app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.service';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DatePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
        jest.resetAllMocks();
    });

    it('updates chart data on changes', () => {
        jest.spyOn(comp, 'updateChartDataSet');
        jest.spyOn(injectorService, 'filterComparisons').mockReturnValue([]);

        comp.ngOnChanges({
            plagiarismResult: { currentValue: plagiarismResult } as SimpleChange,
        });

        expect(comp.updateChartDataSet).toHaveBeenCalledOnce();
        for (let i = 0; i < 10; i++) {
            expect(comp.ngxData[i].value).toBe(plagiarismResult.similarityDistribution[i]);
        }
    });

    it('updates the chart data correctly', () => {
        expect(comp.ngxData).toHaveLength(0);

        comp.updateChartDataSet([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);

        expect(comp.ngxData).toHaveLength(10);
    });

    it('sets BucketDTOs', () => {
        const filterComparisonsMock = jest.spyOn(injectorService, 'filterComparisons').mockReturnValue([]);

        comp.ngOnChanges({
            plagiarismResult: { currentValue: plagiarismResult } as SimpleChange,
        });

        expect(filterComparisonsMock).toHaveBeenCalledTimes(10);
        expect(comp.bucketDTOs).toHaveLength(10);
    });

    it.each([0, 10, 20, 30, 40, 50, 60, 70, 80, 90])('emits the correct range if bar is selected', (minimumBorder: number) => {
        const similaritySelectedStub = jest.spyOn(comp.similaritySelected, 'emit').mockImplementation();
        const maximumBorder = minimumBorder + 10;

        const event = { name: '[' + minimumBorder + '%-' + maximumBorder + '%)' };

        comp.onSelect(event);

        expect(similaritySelectedStub).toHaveBeenCalledOnce();
        expect(similaritySelectedStub).toHaveBeenCalledWith(new Range(minimumBorder, maximumBorder));
        jest.restoreAllMocks();
    });

    it.each([1, 2, 3])('return correct bucketDTO', (label: number) => {
        comp.ngxChartLabels = ['1', '2', '3'];
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
        const expected = created.locale('en').format(ArtemisDatePipe.format('en', 'short'));

        fixture.componentRef.setInput('plagiarismResult', { ...plagiarismResult, createdDate: created } as any);
        fixture.detectChanges();

        const items = fixture.nativeElement.querySelectorAll('.plagiarism-run-details-stats-item');
        const startedAtInfo: HTMLElement = items[4].querySelector('.plagiarism-run-details-info');
        expect(startedAtInfo.textContent?.trim()).toBe(expected);
    });

    it('formats duration as HH:mm:ss in GMT', () => {
        const durationMs = 5200; // 5.2 seconds
        fixture.componentRef.setInput('plagiarismResult', { ...plagiarismResult, duration: durationMs } as any);
        fixture.detectChanges();

        const expected = new Date(durationMs).toISOString().substring(11, 19);

        const items = fixture.nativeElement.querySelectorAll('.plagiarism-run-details-stats-item');
        const durationInfo: HTMLElement = items[3].querySelector('.plagiarism-run-details-info.duration');
        expect(durationInfo.textContent?.trim()).toBe(expected);
    });

    it('shows empty created date when not provided', () => {
        fixture.componentRef.setInput('plagiarismResult', { ...plagiarismResult, createdDate: undefined } as any);
        fixture.detectChanges();

        const items = fixture.nativeElement.querySelectorAll('.plagiarism-run-details-stats-item');
        const startedAtInfo: HTMLElement = items[4].querySelector('.plagiarism-run-details-info');
        expect(startedAtInfo.textContent?.trim()).toBe('');
    });
});
