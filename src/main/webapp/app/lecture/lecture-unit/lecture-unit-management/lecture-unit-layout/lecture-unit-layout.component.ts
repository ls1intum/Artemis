import { Component, Input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-lecture-unit-layout',
    templateUrl: './lecture-unit-layout.component.html',
    imports: [TranslateDirective],
})
export class LectureUnitLayoutComponent {
    @Input() isLoading = false;
}
