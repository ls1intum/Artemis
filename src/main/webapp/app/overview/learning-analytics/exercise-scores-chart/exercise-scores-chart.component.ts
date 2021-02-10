import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { Color, Label } from 'ng2-charts';

@Component({
    selector: 'jhi-exercise-scores-chart',
    templateUrl: './exercise-scores-chart.component.html',
    styleUrls: ['./exercise-scores-chart.component.scss'],
})
export class ExerciseScoresChartComponent implements OnInit, AfterViewInit {
    @ViewChild('chartDiv') chartDiv: ElementRef;
    public lineChartData: ChartDataSets[] = [{ data: [65, 59, 80, 81, 56, 55, 40], label: 'Series A' }];
    public lineChartLabels: Label[] = ['January', 'February', 'March', 'April', 'May', 'June', 'July'];
    public lineChartOptions: ChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
    };
    public lineChartColors: Color[] = [
        {
            borderColor: 'black',
            backgroundColor: 'rgba(255,0,0,0.3)',
        },
    ];
    public lineChartLegend = true;
    public lineChartType: ChartType = 'line';
    public lineChartPlugins = [];

    constructor() {}

    ngOnInit() {}

    ngAfterViewInit() {
        // Todo: Calculate this width depending on the number of exercises to make graph scrollable
        this.chartDiv.nativeElement.setAttribute('style', `width: 3000px`);
    }
}
