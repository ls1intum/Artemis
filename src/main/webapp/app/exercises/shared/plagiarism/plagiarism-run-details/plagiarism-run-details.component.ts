import { Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';

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
     * Directive to manage the canvas element that renders the chart.
     */
    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    /**
     * The labels of the chart are fixed and represent the 10 intervals we group the similarities into.
     */
    chartLabels: Label[] = ['0%-10%', '10%-20%', '20%-30%', '30%-40%', '40%-50%', '50%-60%', '60%-70%', '70%-80%', '80%-90%', '90%-100%'];

    /**
     * The similarity distribution is visualized in a bar chart.
     */
    chartType: ChartType = 'bar';

    /**
     * Array of datasets to plot.
     *
     * Only one dataset is necessary to display the similarity distribution.
     */
    chartDataSets: ChartDataSets[] = [
        {
            backgroundColor: 'lightskyblue',
            data: [],
            hoverBackgroundColor: 'dodgerblue',
        },
    ];

    /**
     * When visualizing the similarity distribution, we always want the y-axis to begin at zero.
     * Also, since values on the y-axis will always be integers, we set the step size to 1.
     */
    chartOptions: ChartOptions = {
        scales: {
            yAxes: [
                {
                    ticks: {
                        beginAtZero: true,
                        stepSize: 1,
                    },
                },
            ],
        },
    };

    ngOnChanges(changes: SimpleChanges) {
        if (changes.plagiarismResult) {
            this.updateChartSetData(changes.plagiarismResult.currentValue.similarityDistribution || []);
        }
    }

    /**
     * Update the data of the dataset at the given index.
     *
     * @param data  - the updated data array
     * @param index - index of the dataset to update (default: 0)
     */
    updateChartSetData(data: number[], index = 0) {
        if (!this.chartDataSets.length || index >= this.chartDataSets.length) {
            return;
        }

        this.chartDataSets[index].data = data;
    }
}
