import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { Color, ScaleType } from '@swimlane/ngx-charts';

@Component({
    selector: 'jhi-plagiarism-run-details',
    styleUrls: ['./plagiarism-run-details.component.scss'],
    templateUrl: './plagiarism-run-details.component.html',
})
export class PlagiarismRunDetailsComponent implements OnChanges {
    /**
     * Result of the automated plagiarism detection
     */
    @Input() plagiarismResult: TextPlagiarismResult | ModelingPlagiarismResult;

    /**
     * The labels of the chart are fixed and represent the 10 intervals we group the similarities into.
     */
    ngxChartLabels: string[] = ['0%-10%', '10%-20%', '20%-30%', '30%-40%', '40%-50%', '50%-60%', '60%-70%', '70%-80%', '80%-90%', '90%-100%'];

    /**
     * The similarity distribution is visualized in a bar chart.
     */

    ngxColor = {
        name: 'similarity distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['#87cefa'], // color: light blue
    } as Color;
    ngxData: any[] = [];
    yAxisTicks: number[] = [];

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
        this.yAxisTicks = Array.from(Array(maxValue + 1).keys());
        let ngxDataEntity;
        data.forEach((value, position) => {
            ngxDataEntity = { name: this.ngxChartLabels[position], value };
            this.ngxData.push(ngxDataEntity);
        });

        this.ngxData = [...this.ngxData];
    }

    /**
     * Formats the labels on the y axis in order to display only integer values
     * @param tick the default y axis tick
     * @returns modified y axis tick
     */
    yAxisTickFormatting(tick: string): string {
        return parseFloat(tick).toFixed(0);
    }
}
