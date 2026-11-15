import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleQuestion } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';

/**
 * The round question-mark button next to a label of the Advanced view, which explains the field in a tooltip. It is a button so keyboard users reach the
 * explanation as well.
 */
@Component({
    selector: 'jhi-security-help',
    template: `<button type="button" class="sp-help" [attr.aria-label]="text()" [tumAetUiTooltip]="text()"><fa-icon [icon]="faCircleQuestion" /></button>`,
    styleUrl: './security-policies.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TumAetUiTooltipDirective],
    host: { class: 'inline-flex' },
})
export class SecurityHelpComponent {
    protected readonly faCircleQuestion = faCircleQuestion;

    /** The translated explanation. */
    readonly text = input.required<string>();
}
