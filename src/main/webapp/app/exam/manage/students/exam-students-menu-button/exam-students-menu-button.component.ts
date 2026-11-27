import { Component, input } from '@angular/core';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons/faChevronDown';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective, TumAetUiButtonSeverity } from '@tumaet/ui-angular/button';
import { TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective } from '@tumaet/ui-angular/menu';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export interface ExamStudentsMenuItem {
    /** Translation key of the entry label. */
    label: string;
    icon?: IconDefinition;
    /** Translation key of the hint shown on hover. */
    tooltip?: string;
    disabled?: boolean;
    /** Renders the entry in the danger colour, for destructive actions. */
    danger?: boolean;
    command: () => void;
}

@Component({
    selector: 'jhi-exam-students-menu-button',
    templateUrl: './exam-students-menu-button.component.html',
    imports: [
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        TumAetUiTooltipDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
    ],
})
export class ExamStudentsMenuButtonComponent {
    readonly model = input.required<ExamStudentsMenuItem[]>();
    readonly label = input.required<string>();
    readonly buttonIcon = input.required<IconDefinition>();
    readonly disabled = input(false);
    readonly severity = input<TumAetUiButtonSeverity>('primary');

    protected readonly faChevronDown = faChevronDown;
}
