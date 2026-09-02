import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiSeverity, TumUiSeverityAlias, resolveSeverity } from '../foundation/tum-ui-vocabulary';

/** Severities a message can carry. A subset of {@link TumUiSeverity}. */
export type TumUiMessageSeverity = Extract<TumUiSeverity, 'info' | 'success' | 'warning' | 'danger' | 'secondary' | 'contrast'>;

const MESSAGE_BASE = 'tum-ui-message';

const MESSAGE_SEVERITY: Record<TumUiMessageSeverity, string> = {
    // The four semantic surfaces are `color-mix()`ed against the page background in SCSS, keyed off
    // `data-severity`, because a mix against the surface behind the element is not expressible as a utility.
    info: '',
    success: '',
    warning: '',
    danger: '',
    secondary: 'tum:bg-hover-background tum:text-text tum:outline-border',
    contrast: 'tum:bg-contrast-background tum:text-contrast tum:outline-contrast-background',
};

/**
 * A statement about the surface it sits on: what happened, what is true now, what to do next.
 *
 * Give the text through `text` or by projection; both render, in that order, so a sentence and the button that
 * resolves it can live in the same message.
 *
 * **It is not a live region unless you say so.** `live` defaults to `false`, because severity alone cannot decide
 * this: five permanently-rendered messages on one page would otherwise be five live regions competing to announce
 * themselves on load, and an error rendered with the page would announce nothing at all, because a live region
 * only fires on a *change* after it exists. Turn `live` on for the one message that reports the outcome of
 * something the user just did — a `danger` message then becomes an assertive `alert`, everything else a polite
 * `status`.
 */
@Component({
    selector: 'tum-ui-message',
    templateUrl: './tum-ui-message.component.html',
    styleUrl: './tum-ui-message.component.scss',
    imports: [FaIconComponent],
    host: {
        '[attr.role]': 'messageRole()',
        '[class]': 'hostClasses()',
        '[attr.data-slot]': '"message"',
        '[attr.data-severity]': 'effectiveSeverity()',
        '[attr.data-live]': 'live() || null',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiMessageComponent {
    /** Colour role. `error` is accepted as a deprecated spelling of `danger`, `warn` of `warning`. */
    readonly severity = input<TumUiMessageSeverity | TumUiSeverityAlias>('info');

    /** Message text. It renders alongside projected content rather than replacing it. */
    readonly text = input<string>();

    /** Leading icon. Decorative: the message's own words carry the meaning. */
    readonly icon = input<IconProp>();

    /**
     * Announces the message when it appears or changes. Leave it off for a message that is simply part of the
     * page; turn it on for at most one message per surface.
     */
    readonly live = input(false, { transform: booleanAttribute });

    protected readonly effectiveSeverity = computed(() => resolveSeverity<TumUiMessageSeverity>(this.severity(), 'tum-ui-message'));

    protected readonly messageRole = computed(() => {
        if (!this.live()) {
            return null;
        }
        return this.effectiveSeverity() === 'danger' ? 'alert' : 'status';
    });

    protected readonly hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.effectiveSeverity()]}`.trim());
}
