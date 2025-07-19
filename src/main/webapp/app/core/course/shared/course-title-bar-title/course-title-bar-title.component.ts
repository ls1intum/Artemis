import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-title-bar-title',
    imports: [TranslateDirective],
    templateUrl: './course-title-bar-title.component.html',
})
export class CourseTitleBarTitleComponent {
    id = input<string>('course-title-bar-title');
    title = input.required<string>();
}
