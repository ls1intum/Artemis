import { TumAetUiButtonDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';

import { Component, computed, input, model } from '@angular/core';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faGraduationCap } from '@fortawesome/free-solid-svg-icons';

import { TranslateDirective } from 'app/foundation/language/translate.directive';

export type ParticipationMode = 'practice' | 'graded';

interface ParticipationModeOption {
    value: ParticipationMode;
    label: string;
    icon: IconDefinition;
}

@Component({
    selector: 'jhi-participation-mode-toggle',
    templateUrl: './participation-mode-toggle.component.html',
    styleUrls: ['./participation-mode-toggle.component.scss'],
    imports: [TumAetUiButtonDirective, TumAetUiTagComponent, FaIconComponent, TranslateDirective],
})
export class ParticipationModeToggleComponent {
    readonly mode = model.required<ParticipationMode>();
    readonly hasPractice = input(false);
    readonly hasGraded = input(false);

    readonly faBook = faBook;
    readonly faGraduationCap = faGraduationCap;

    /** Both modes available → the user can switch between them (action). */
    readonly canToggle = computed(() => this.hasPractice() && this.hasGraded());

    readonly modeOptions: ParticipationModeOption[] = [
        { value: 'practice', label: 'artemisApp.exerciseActions.mode.practice', icon: faBook },
        { value: 'graded', label: 'artemisApp.exerciseActions.mode.graded', icon: faGraduationCap },
    ];

    selectMode(mode: ParticipationMode) {
        this.mode.set(mode);
    }
}
