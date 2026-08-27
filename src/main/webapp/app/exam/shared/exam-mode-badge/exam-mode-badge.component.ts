import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiTagComponent } from '@tumaet/ui-angular';
import { faGraduationCap } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-mode-badge',
    templateUrl: './exam-mode-badge.component.html',
    imports: [TranslateDirective, TumUiTagComponent],
})
export class ExamModeBadgeComponent {
    testExam = input.required<boolean>();

    protected readonly faGraduationCap = faGraduationCap;
}
