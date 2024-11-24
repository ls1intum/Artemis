import { Directive, Input } from '@angular/core';

@Directive({
    selector: '[jhiTranslate]',
    standalone: true,
})
export class MockJhiTranslateDirective {
    @Input() jhiTranslate: string;
}
