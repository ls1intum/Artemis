import { Directive, input } from '@angular/core';

@Directive({
    selector: '[jhiTranslate]',
})
export class MockJhiTranslateDirective {
    jhiTranslate = input<string>();
}
