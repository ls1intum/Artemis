import { Directive, input } from '@angular/core';

@Directive({ selector: '[translateValues]' })
export class MockTranslateValuesDirective {
    data = input<any>(undefined, { alias: 'translateValues' });
}
