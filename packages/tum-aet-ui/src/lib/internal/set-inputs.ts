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
 * Sets inputs on a component that the library creates in code, with the input names and value types checked at compile time.
 *
 * `ComponentRef.setInput` takes the name as a plain string, so a misspelled or removed input only logs NG0303 at runtime.
 * Inputs are matched by the name of the class member, so an input declared with an `alias` is not supported.
 *
 * The library must not import from the Artemis application, so it keeps this counterpart of `setInputs` in
 * `app/foundation/util/set-inputs.util.ts`. Keep the two in step.
 *
 * @param ref The reference of the created component
 * @param values The inputs to set; omitted inputs keep their current value
 */
export function setInputs<T>(ref: ComponentRef<T>, values: ComponentInputValues<T>): void {
    for (const [name, value] of Object.entries(values)) {
        // eslint-disable-next-line localRules/no-component-ref-set-input -- the typed wrapper around the string based API
        ref.setInput(name, value);
    }
}
