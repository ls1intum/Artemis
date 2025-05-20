import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-card-wrapper',
    templateUrl: './card-wrapper.component.html',
    imports: [TranslateDirective],
})
export class CardWrapperComponent {
    title = input.required<string>();
    maxWidth = input<string>('50rem');
    minHeight = input<string>('91%');
}
