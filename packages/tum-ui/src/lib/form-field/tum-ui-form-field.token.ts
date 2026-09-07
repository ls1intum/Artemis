import { InjectionToken, Signal } from '@angular/core';

/**
 * Contract a {@link TumUiFormFieldComponent} exposes to the control it wraps, so the control can adopt the
 * field's label target, description, and validity without the consumer repeating any of them.
 *
 * A control opts in by injecting {@link TUM_UI_FORM_FIELD} optionally. A component that renders another TUM
 * UI control inside its own view must shadow the token in `viewProviders` and forward what it needs, so the
 * field's wiring is applied once rather than by both the wrapper and the control nested inside it.
 *
 * Ids resolve in one direction at a time, so the label can never point at an element that is not there:
 * an `explicitControlId` set on the field wins and the control adopts it; otherwise a control that brought
 * an id of its own reports it through {@link adoptControlId} and the field labels that; otherwise the
 * control adopts the field's generated {@link labelTargetId}.
 */
export interface TumUiFormFieldContext {
    /** Id the host set explicitly on the field, which overrides any id the control brought. */
    readonly explicitControlId: Signal<string | undefined>;

    /** Id the field's `<label for>` points at; a control without an id of its own adopts it. */
    readonly labelTargetId: Signal<string>;

    /** Space-separated ids of the text currently describing the field, or `undefined` when it shows none. */
    readonly describedBy: Signal<string | undefined>;

    /** Whether the field is currently showing an error, so the control can render its invalid state. */
    readonly invalid: Signal<boolean>;

    /** Reports the id a control brought with it, so the label targets the real control. */
    adoptControlId(id: string): void;
}

export const TUM_UI_FORM_FIELD = new InjectionToken<TumUiFormFieldContext | null>('TumUiFormField');
