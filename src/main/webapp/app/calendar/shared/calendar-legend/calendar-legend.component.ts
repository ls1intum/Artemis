import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-calendar-legend',
    imports: [TranslateDirective],
    templateUrl: './calendar-legend.component.html',
    styleUrl: './calendar-legend.component.scss',
})
export class CalendarLegendComponent {}
