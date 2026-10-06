import { ComponentRef, InputSignalWithTransform } from '@angular/core';

/**
 * The type that can be written to a signal input (`input()`, `input.required()`, `model()`), or `never` for any other member.
 * The input is matched through `infer`: a plain `InputSignalWithTransform<unknown, unknown>` constraint does not match
 * an `InputSignal<string>`, which would turn every member into a non-input and silently accept any object.
 */
type InputWriteType<Member> = Member extends InputSignalWithTransform<infer _Read, infer Write> ? Write : never;

/** Whether a class member is a signal input. Decided from the member alone, so it also resolves for a generic input type. */
type IsInput<Member> = Member extends InputSignalWithTransform<infer _Read, infer _Write> ? true : false;

/** The values that can be written to the signal inputs of a component class, keyed by the class member that declares them. */
type ComponentInputValues<T> = {
    [K in keyof T as IsInput<T[K]> extends true ? K : never]?: InputWriteType<T[K]>;
};

/**
 * Sets inputs on a component that was created in code (`ViewContainerRef.createComponent`, `createComponent`, a portal).
 *
 * `ComponentRef.setInput` takes the input name as a plain string, so a misspelled or removed input is only logged as NG0303
 * at runtime (it does not throw), and only when that code path runs. This wrapper checks the names and the value types
 * against the component class at compile time. Use it instead of calling `setInput` directly; `localRules/no-component-ref-set-input` enforces that.
 *
 * Inputs are matched by the name of the class member, so an input declared with an `alias` is not supported. When the
 * set of components is closed and known, declare them in a template with a `@switch` instead, which the template
 * compiler checks as well.
 *
 * @param ref The reference of the created component
 * @param values The inputs to set; omitted inputs keep their current value
 */
export function setInputs<T>(ref: ComponentRef<T>, values: ComponentInputValues<T>): void {
    for (const [name, value] of Object.entries(values)) {
        // This is the single place that may call `setInput`; every caller above it is type checked.
        // eslint-disable-next-line localRules/no-component-ref-set-input -- the typed wrapper around the string based API
        ref.setInput(name, value);
    }
}
