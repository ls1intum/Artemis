import { Directive, Input } from '@angular/core';

@Directive({
    selector: '[jhiTranslate]',
})
export class MockJhiTranslateDirective {
    @Input() jhiTranslate: string;
}
