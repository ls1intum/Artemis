import { Directive, Input } from '@angular/core';

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[translateValues]' })
export class MockTranslateValuesDirective {
    @Input('translateValues') data: any;
}
