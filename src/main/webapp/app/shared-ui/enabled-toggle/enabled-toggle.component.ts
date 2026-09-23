import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';

/**
 * A two-button Enabled / Disabled segmented control, used for the course-level AI feature switches (Iris, Athena).
 *
 * Purely presentational: it renders the current state and reports the state the user asked for. Persisting that state,
 * and rolling it back when the request fails, is the caller's job.
 *
 * The colours live here rather than in a Tailwind utility because Angular's emulated encapsulation gives a component's
 * own selectors higher specificity than a utility class, so a `bg-*` binding would be overridden by the base button
 * rule and the active state would render uncoloured.
 */
@Component({
    selector: 'jhi-enabled-toggle',
    imports: [TranslateDirective, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <div class="enabled-toggle-group" role="group" [attr.aria-label]="ariaLabel()">
            <button
                type="button"
                class="enabled-toggle-btn"
                [class.enabled-toggle-btn--active-on]="enabled()"
                [attr.aria-pressed]="enabled()"
                [attr.data-testid]="testId() ? testId() + '-enable' : undefined"
                (click)="enabledChange.emit(true)"
            >
                <fa-icon [icon]="faCheck" />
                <span jhiTranslate="global.generic.enabled"></span>
            </button>
            <button
                type="button"
                class="enabled-toggle-btn"
                [class.enabled-toggle-btn--active-off]="!enabled()"
                [attr.aria-pressed]="!enabled()"
                [attr.data-testid]="testId() ? testId() + '-disable' : undefined"
                (click)="enabledChange.emit(false)"
            >
                <fa-icon [icon]="faTimes" />
                <span jhiTranslate="global.generic.disabled"></span>
            </button>
        </div>
    `,
    styles: [
        `
            :host {
                display: block;
                width: 100%;

                // The control is sized by the panel around it, not by the viewport: on a phone the
                // app shell leaves a panel about 200px, less than the buttons want at full padding.
                container-type: inline-size;
            }

            .enabled-toggle-group {
                display: flex;
                border: 1px solid var(--p-content-border-color);
                border-radius: 0.625rem;
                overflow: hidden;
            }

            .enabled-toggle-btn {
                flex: 1;
                min-width: 0;

                // The icon sits next to its label rather than above it, and the pair
                // stays centred as the two buttons share the width between them. Only
                // when even the narrow padding below leaves too little room does the
                // icon wrap above its label, since the group clips what overflows it.
                display: inline-flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: center;
                gap: 0.15rem 0.4rem;

                padding: 0.5rem 1rem;
                border: none;
                background: var(--overview-card-nested-bg, var(--p-content-background));
                color: var(--p-text-muted-color);
                font-weight: 500;
                font-size: 0.88rem;
                cursor: pointer;
                transition: all 0.2s ease;

                &:first-child {
                    border-right: 1px solid var(--p-content-border-color);
                }

                &--active-on {
                    background: var(--tumaet-ui-state-success);
                    color: var(--tumaet-ui-state-success-contrast);
                    font-weight: 600;
                }

                &--active-off {
                    background: var(--tumaet-ui-state-danger);
                    color: var(--tumaet-ui-state-danger-contrast);
                    font-weight: 600;
                }
            }

            @container (max-width: 240px) {
                .enabled-toggle-btn {
                    padding-inline: 0.5rem;
                }
            }
        `,
    ],
})
export class EnabledToggleComponent {
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    readonly enabled = input.required<boolean>();
    /** Describes what is being switched, for screen readers. */
    readonly ariaLabel = input<string>();
    /** When set, the two buttons get `<testId>-enable` and `<testId>-disable` as their test ids. */
    readonly testId = input<string>();

    readonly enabledChange = output<boolean>();
}
