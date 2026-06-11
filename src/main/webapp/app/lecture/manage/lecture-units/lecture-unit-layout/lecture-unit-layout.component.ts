import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-lecture-unit-layout',
    templateUrl: './lecture-unit-layout.component.html',
    imports: [TranslateDirective],
})
export class LectureUnitLayoutComponent {
    isLoading = input<boolean>(false);
}
