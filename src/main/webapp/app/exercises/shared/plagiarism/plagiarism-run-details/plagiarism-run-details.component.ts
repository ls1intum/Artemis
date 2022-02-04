import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismAndTutorEffortDirective } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-and-tutor-effort.directive';
import { GraphColors } from 'app/entities/statistics.model';
import { round } from 'app/shared/util/utils';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { PlagiarismInspectorService } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.service';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

export interface SimilarityRange {
    minimumSimilarity: number;
    maximumSimilarity: number;
}

@Component({
    selector: 'jhi-plagiarism-run-details',
    styleUrls: ['./plagiarism-run-details.component.scss', '../../../../shared/chart/vertical-bar-chart.scss'],
    templateUrl: './plagiarism-run-details.component.html',
})
export class PlagiarismRunDetailsComponent extends PlagiarismAndTutorEffortDirective implements OnChanges {
    /**
     * Result of the automated plagiarism detection
     */
    @Input() plagiarismResult: TextPlagiarismResult | ModelingPlagiarismResult;
    @Output() similaritySelected: EventEmitter<SimilarityRange> = new EventEmitter<SimilarityRange>();

    yScaleMax = 5;
    totalDetectedPlagiarisms: number;
    additionalInformation: any[] = [];

    readonly round = round;
    readonly stringify = JSON.stringify;

    constructor(private inspectorService: PlagiarismInspectorService) {
        super();
        /**
         * The labels of the chart are fixed and represent the 10 intervals we group the similarities into.
         */
        this.ngxChartLabels = ['[0%-10%)', '[10%-20%)', '[20%-30%)', '[30%-40%)', '[40%-50%)', '[50%-60%)', '[60%-70%)', '[70%-80%)', '[80%-90%)', '[90%-100%]'];
        this.ngxColor.domain = [...Array(8).fill(GraphColors.LIGHT_BLUE), ...Array(2).fill(GraphColors.RED)];
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.plagiarismResult) {
            this.setAdditionalInformation(changes.plagiarismResult.currentValue.comparisons || []);
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

    private setAdditionalInformation(comparisons: PlagiarismComparison<any>[]) {
        const steps = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90];
        let comparisonsWithinRange;
        let additionInformationEntry;
        steps.forEach((minimumSimilarity) => {
            comparisonsWithinRange = this.inspectorService.filterComparisons({ minimumSimilarity, maximumSimilarity: minimumSimilarity + 10 }, comparisons);
            additionInformationEntry = {
                confirmed: comparisonsWithinRange.filter((comparison) => comparison.status === PlagiarismStatus.CONFIRMED).length,
                denied: comparisonsWithinRange.filter((comparison) => comparison.status === PlagiarismStatus.DENIED).length,
                open: comparisonsWithinRange.filter((comparison) => comparison.status === PlagiarismStatus.NONE).length,
            };
            this.additionalInformation.push(additionInformationEntry);
        });
    }

    getAdditionalInformation(label: string) {
        const index = this.ngxChartLabels.indexOf(label);
        console.log(this.additionalInformation[index]);
        return this.additionalInformation[index];
    }

    onSelect(event: any) {
        const interval = event.name as string;
        const separatorIndex = interval.indexOf('-');
        const minimumSimilarity = parseInt(interval.slice(1, separatorIndex), 10);
        const maximumSimilarity = parseInt(interval.slice(separatorIndex + 1, interval.length - 2), 10);
        console.log(minimumSimilarity + ' - ' + maximumSimilarity);

        this.similaritySelected.emit({ maximumSimilarity, minimumSimilarity });
    }
}
