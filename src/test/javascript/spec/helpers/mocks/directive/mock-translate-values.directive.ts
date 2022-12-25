import { Directive, Input } from '@angular/core';

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: '[translateValues]' })
export class MockTranslateValuesDirective {
    @Input('translateValues') data: any;
}
