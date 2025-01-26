import { Directive, Input } from '@angular/core';

@Directive({ selector: '[translateValues]' })
export class MockTranslateValuesDirective {
    @Input('translateValues') data: any;
}
