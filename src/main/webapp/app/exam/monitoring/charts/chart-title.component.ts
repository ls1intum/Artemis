import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-chart-title',
    templateUrl: './chart-title.component.html',
})
export class ChartTitleComponent {
    @Input()
    routerLink?: any[];
    @Input()
    nameTranslationValue: string;
    @Input()
    tooltipTranslationValue: string;

    constructor() {}
}
