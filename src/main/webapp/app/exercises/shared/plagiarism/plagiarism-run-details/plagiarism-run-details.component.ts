import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismAndTutorEffortDirective } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-and-tutor-effort.directive';

@Component({
    selector: 'jhi-plagiarism-run-details',
    styleUrls: ['./plagiarism-run-details.component.scss'],
    templateUrl: './plagiarism-run-details.component.html',
})
export class PlagiarismRunDetailsComponent extends PlagiarismAndTutorEffortDirective implements OnChanges {
    /**
     * Result of the automated plagiarism detection
     */
    @Input() plagiarismResult: TextPlagiarismResult | ModelingPlagiarismResult;

    yAxisTicks: number[];

    constructor() {
        super();
        /**
         * The labels of the chart are fixed and represent the 10 intervals we group the similarities into.
         */
        this.ngxChartLabels = ['0%-10%', '10%-20%', '20%-30%', '30%-40%', '40%-50%', '50%-60%', '60%-70%', '70%-80%', '80%-90%', '90%-100%'];
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.plagiarismResult) {
            this.updateChartDataSet(changes.plagiarismResult.currentValue.similarityDistribution || []);
        }
    }

    /**
     * Update the data of the dataset at the given index.
     *
     * @param data  - the updated data array
     */
    updateChartDataSet(data: number[]) {
        const maxValue = Math.max(...data);
        this.yAxisTicks = Array.from(new Array(maxValue + 1).keys());
        let ngxDataEntity;
        this.ngxData = [];
        data.forEach((value, position) => {
            ngxDataEntity = { name: this.ngxChartLabels[position], value };
            this.ngxData.push(ngxDataEntity);
        });

        this.ngxData = [...this.ngxData];
    }
}
