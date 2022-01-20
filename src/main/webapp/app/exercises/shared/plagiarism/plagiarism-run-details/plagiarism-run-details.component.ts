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

    /*
    ngxTestData is for testing purposes on your local setup as
    it might be a bit difficult to simulate such amounts of plagiarisms on the test servers.
    If you want to inject the data into the chart, replace line 19 in the corresponding html template with the following:

    [results]="ngxTestData"

    Feel free to manipulate the values in order to check the behavior of the chart for only small values (< 10)
     */

    /*ngxTestData = [
        { name: '0%-10%', value: 0 },
        { name: '10%-20%', value: 0 },
        { name: '20%-30%', value: 0 },
        { name: '30%-40%', value: 0 },
        { name: '40%-50%', value: 13 },
        { name: '50%-60%', value: 24 },
        { name: '60%-70%', value: 36 },
        { name: '70%-80%', value: 41 },
        { name: '80%-90%', value: 57 },
        { name: '90%-100%', value: 0 },
    ];*/
    yScaleMax = 5;

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
        let ngxDataEntity;
        this.ngxData = [];
        data.forEach((value, position) => {
            ngxDataEntity = { name: this.ngxChartLabels[position], value };
            this.ngxData.push(ngxDataEntity);
        });

        this.ngxData = [...this.ngxData];
    }
}
