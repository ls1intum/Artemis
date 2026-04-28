import { Component, input, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export type ParticipationMode = 'practice' | 'graded';

@Component({
    selector: 'jhi-participation-mode-toggle',
    templateUrl: './participation-mode-toggle.component.html',
    styleUrls: ['./participation-mode-toggle.component.scss'],
    imports: [FaIconComponent, TranslateDirective],
})
export class ParticipationModeToggleComponent {
    readonly mode = model.required<ParticipationMode>();
    readonly hasPractice = input(false);
    readonly hasBoth = input(false);

    readonly faBook = faBook;
    readonly faGraduationCap = faGraduationCap;

    selectMode(mode: ParticipationMode) {
        this.mode.set(mode);
    }
}
