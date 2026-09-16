import { Component, computed, effect, inject, input, output, untracked } from '@angular/core';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { Range, round } from 'app/foundation/util/utils';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismResultStatsDTO } from 'app/plagiarism/shared/entities/PlagiarismResultDTO';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { singleSeriesChart } from 'app/shared-ui/chart/tum-ui-chart-adapters';
import { DatePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { PlagiarismAndTutorEffortDirective } from 'app/plagiarism/manage/plagiarism-run-details/plagiarism-and-tutor-effort.directive';
import { PlagiarismInspectorService } from 'app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.service';
import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';
import { TumUiBarChartComponent, TumUiBarChartConfig, TumUiChartSelectEvent } from '@tumaet/ui-angular';

interface SimilarityRangeComparisonStateDTO {
    confirmed: number;
    denied: number;
    open: number;
}

@Component({
    selector: 'jhi-plagiarism-run-details',
    styleUrls: ['./plagiarism-run-details.component.scss'],
    templateUrl: './plagiarism-run-details.component.html',
    imports: [TranslateDirective, HelpIconComponent, TumUiBarChartComponent, DatePipe, ArtemisTranslatePipe, ArtemisDatePipe],
})
export class PlagiarismRunDetailsComponent extends PlagiarismAndTutorEffortDirective {
    private inspectorService = inject(PlagiarismInspectorService);
    private translateService = inject(TranslateService);

    /**
     * Result of the automated plagiarism detection
     */
    readonly plagiarismResult = input<PlagiarismResult>();
    /**
     * Statistics for the automated plagiarism detection result
     */
    readonly plagiarismResultStats = input<PlagiarismResultStatsDTO>();
    readonly similaritySelected = output<Range>();

    yScaleMax = 5;
    totalDetectedPlagiarisms = 0;
    bucketDTOs: SimilarityRangeComparisonStateDTO[] = [];

    readonly round = round;

    private readonly resolvedColors = computed(() => this.chartColors());

    readonly chartData = computed(() => singleSeriesChart(this.chartEntries(), this.resolvedColors()));
    readonly chartConfig = computed<TumUiBarChartConfig>(() => ({
        yAxis: { max: this.yScaleMax, tickFormatter: this.yAxisTickFormatting },
        tooltip: {
            title: (items) => {
                const item = items[0];
                if (!item) {
                    return '';
                }
                return this.translateService.instant('artemisApp.plagiarism.numberIdentifiedPairs', { amount: item.value });
            },
            label: (item) => {
                const label = item.label;
                const bucketDTO = this.getBucketDTO(label);
                const percentage = this.totalDetectedPlagiarisms > 0 ? round((item.value * 100) / this.totalDetectedPlagiarisms, 2) : 0;
                return [
                    this.translateService.instant('artemisApp.plagiarism.confirmed', { amount: bucketDTO?.confirmed ?? 0 }),
                    this.translateService.instant('artemisApp.plagiarism.denied', { amount: bucketDTO?.denied ?? 0 }),
                    this.translateService.instant('artemisApp.plagiarism.open', { amount: bucketDTO?.open ?? 0 }),
                    this.translateService.instant('artemisApp.plagiarism.withSimilarity', { range: label }),
                    this.translateService.instant('artemisApp.plagiarism.portionOfAllCases', { percentage }),
                ];
            },
        },
        dataLabels: { formatter: (value) => `${value}` },
    }));

    constructor() {
        super();
        /**
         * The labels of the chart are fixed and represent the 10 intervals we group the similarities into.
         */
        this.chartLabels = ['[0%-10%)', '[10%-20%)', '[20%-30%)', '[30%-40%)', '[40%-50%)', '[50%-60%)', '[60%-70%)', '[70%-80%)', '[80%-90%)', '[90%-100%]'];
        this.chartColors.set([...Array(8).fill(GraphColors.LIGHT_BLUE), ...Array(2).fill(GraphColors.RED)]);

        // Replaces ngOnChanges: rebuild the chart buckets and dataset whenever the plagiarism result changes.
        // The writes to bucketDTOs/chartEntries run untracked so only plagiarismResult() retriggers the effect.
        effect(() => {
            const plagiarismResult = this.plagiarismResult();
            if (plagiarismResult) {
                untracked(() => {
                    this.setBucketDTOs(plagiarismResult.comparisons || []);
                    this.updateChartDataSet(plagiarismResult.similarityDistribution || []);
                });
            }
        });
    }

    /**
     * Update the data of the dataset at the given index.
     *
     * @param data  - the updated data array
     */
    updateChartDataSet(data: number[]) {
        this.chartEntries.set(data.map((value, position) => ({ name: this.chartLabels[position], value })));
        this.totalDetectedPlagiarisms = data.reduce((number1, number2) => number1 + number2, 0);
    }

    /**
     * Auxiliary method that sets the comparison dtos for the different chart buckets. These are used for the chart tooltips
     * to show the number of confirmed, denied and open plagiarism cases
     * @param comparisons the pairs identified by the detection tool
     */
    private setBucketDTOs(comparisons: PlagiarismComparison[]): void {
        this.bucketDTOs = [];
        // we use this array as minimum similarities for the filtering
        const steps = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90];
        let comparisonsWithinRange;
        let additionInformationEntry;
        steps.forEach((minimumSimilarity) => {
            comparisonsWithinRange = this.inspectorService.filterComparisons(new Range(minimumSimilarity, minimumSimilarity + 10), comparisons);
            additionInformationEntry = {
                confirmed: comparisonsWithinRange.filter((comparison) => comparison.status === PlagiarismStatus.CONFIRMED).length,
                denied: comparisonsWithinRange.filter((comparison) => comparison.status === PlagiarismStatus.DENIED).length,
                open: comparisonsWithinRange.filter((comparison) => comparison.status === PlagiarismStatus.NONE).length,
            };
            this.bucketDTOs.push(additionInformationEntry);
        });
    }

    /**
     * Returns the DTO for a specific bucket
     * @param label the bar label the DTO should be returned for
     */
    getBucketDTO(label: string): SimilarityRangeComparisonStateDTO {
        const index = this.chartLabels.indexOf(label);
        return this.bucketDTOs[index];
    }

    /**
     * Handles the click on a specific chart bar
     * Emits the selected range to {@link PlagiarismInspectorComponent#filterByChart} so that the comparisons shown in the sidebar can be filtered accordingly
     * @param event the event identifying the clicked bar
     */
    onSelect(event: TumUiChartSelectEvent): void {
        const interval = event.label;
        if (!interval) {
            return;
        }
        const separatorIndex = interval.indexOf('-');
        const lowerBound = parseInt(interval.slice(1, separatorIndex), 10);
        const upperBound = parseInt(interval.slice(separatorIndex + 1, interval.length - 2), 10);

        this.similaritySelected.emit(new Range(lowerBound, upperBound));
    }
}
