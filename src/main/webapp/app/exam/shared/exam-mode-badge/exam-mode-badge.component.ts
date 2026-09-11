import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiTagComponent } from '@tumaet/ui-angular';
import { faFlaskVial, faGraduationCap, faVial } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-exam-mode-badge',
    templateUrl: './exam-mode-badge.component.html',
    imports: [TranslateDirective, TumUiTagComponent, FaIconComponent],
    host: { class: 'inline-flex items-center' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExamModeBadgeComponent {
    examMode = input.required<ExamMode>();

    protected readonly ExamMode = ExamMode;
    protected readonly faFlaskVial = faFlaskVial;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faVial = faVial;
}
