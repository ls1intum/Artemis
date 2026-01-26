import { Component, OnChanges, SimpleChanges, inject, input, output } from '@angular/core';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { Range, round } from 'app/shared/util/utils';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismResultStats } from 'app/plagiarism/shared/entities/PlagiarismResultDTO';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { DatePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { PlagiarismAndTutorEffortDirective } from 'app/plagiarism/manage/plagiarism-run-details/plagiarism-and-tutor-effort.directive';
import { PlagiarismInspectorService } from 'app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.service';
import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';

interface SimilarityRangeComparisonStateDTO {
    confirmed: number;
    denied: number;
    open: number;
}

@Component({
    selector: 'jhi-plagiarism-run-details',
    styleUrls: ['./plagiarism-run-details.component.scss', '../../../shared/chart/vertical-bar-chart.scss'],
    templateUrl: './plagiarism-run-details.component.html',
    imports: [TranslateDirective, HelpIconComponent, BarChartModule, DatePipe, ArtemisTranslatePipe, ArtemisDatePipe],
})
export class PlagiarismRunDetailsComponent extends PlagiarismAndTutorEffortDirective implements OnChanges {
    private inspectorService = inject(PlagiarismInspectorService);

    /**
     * Result of the automated plagiarism detection
     */
    readonly plagiarismResult = input<PlagiarismResult>();
    /**
     * Statistics for the automated plagiarism detection result
     */
    readonly plagiarismResultStats = input<PlagiarismResultStats>();
    readonly similaritySelected = output<Range>();

    yScaleMax = 5;
    totalDetectedPlagiarisms: number;
    bucketDTOs: SimilarityRangeComparisonStateDTO[] = [];

    readonly round = round;

    constructor() {
        super();
        /**
         * The labels of the chart are fixed and represent the 10 intervals we group the similarities into.
         */
        this.ngxChartLabels = ['[0%-10%)', '[10%-20%)', '[20%-30%)', '[30%-40%)', '[40%-50%)', '[50%-60%)', '[60%-70%)', '[70%-80%)', '[80%-90%)', '[90%-100%]'];
        this.ngxColor.domain = [...Array(8).fill(GraphColors.LIGHT_BLUE), ...Array(2).fill(GraphColors.RED)];
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.plagiarismResult) {
            this.setBucketDTOs(changes.plagiarismResult.currentValue.comparisons || []);
            this.updateChartDataSet(changes.plagiarismResult.currentValue.similarityDistribution || []);
        }
    }

    /**
     * Update the data of the dataset at the given index.
     *
     * @param data  - the updated data array
     */
    updateChartDataSet(data: number[]) {
        let ngxDataEntity;
        this.ngxData = [];
        data.forEach((value, position) => {
            ngxDataEntity = {
                name: this.ngxChartLabels[position],
                value,
            };
            this.ngxData.push(ngxDataEntity);
        });
        this.totalDetectedPlagiarisms = data.reduce((number1, number2) => number1 + number2, 0);
        this.ngxData = [...this.ngxData];
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
        const index = this.ngxChartLabels.indexOf(label);
        return this.bucketDTOs[index];
    }

    /**
     * Handles the click on a specific chart bar
     * Emits the selected range to {@link PlagiarismInspectorComponent#filterByChart} so that the comparisons shown in the sidebar can be filtered accordingly
     * @param event the event that is passed by ngx-charts
     */
    onSelect(event: any): void {
        const interval = event.name as string;
        const separatorIndex = interval.indexOf('-');
        const lowerBound = parseInt(interval.slice(1, separatorIndex), 10);
        const upperBound = parseInt(interval.slice(separatorIndex + 1, interval.length - 2), 10);

        this.similaritySelected.emit(new Range(lowerBound, upperBound));
    }
}
