import { ComponentRef, InputSignalWithTransform, reflectComponentType } from '@angular/core';

/**
 * The type that can be written to a signal input (`input()`, `input.required()`, `model()`), or `never` for any other member.
 * The input is matched through `infer`: a plain `InputSignalWithTransform<unknown, unknown>` constraint does not match
 * an `InputSignal<string>`, which would turn every member into a non-input and silently accept any object.
 */
type InputWriteType<Member> = Member extends InputSignalWithTransform<infer _Read, infer Write> ? Write : never;

/** Whether a class member is a signal input. Decided from the member alone, so it also resolves for a generic input type. */
type IsInput<Member> = Member extends InputSignalWithTransform<infer _Read, infer _Write> ? true : false;

/** The names of the class members of a component that are signal inputs. */
type InputName<T> = Extract<{ [K in keyof T]-?: IsInput<T[K]> extends true ? K : never }[keyof T], keyof T>;

/**
 * Sets inputs on a component that the library creates in code, with the input names and value types checked at compile time.
 *
 * `ComponentRef.setInput` takes the name as a plain string, so a misspelled or removed input is only logged as NG0303 at
 * runtime. Every input that is passed needs a value of its declared type, so `undefined` is only accepted by an input whose
 * type includes it. Inputs are named by the class member; an input that is declared with an `alias` is set under its alias.
 *
 * The library must not import from the Artemis application, so it keeps this counterpart of `setInputs` in
 * `src/main/webapp/app/foundation/util/set-inputs.util.ts`. The two are meant to be identical in logic and types; change
 * both when you change one.
 *
 * @param ref The reference of the created component
 * @param values The inputs to set, by class member; omitted inputs keep their current value
 */
export function setInputs<T, Name extends InputName<T>>(ref: ComponentRef<T>, values: { [K in Name]: InputWriteType<T[K]> }): void {
    // `setInput` looks an input up by its public (template) name, which differs from the class member for an aliased input.
    const declaredInputs = reflectComponentType(ref.componentType)?.inputs ?? [];
    for (const [name, value] of Object.entries<unknown>(values)) {
        const publicName = declaredInputs.find((input) => input.propName === name)?.templateName ?? name;
        // eslint-disable-next-line localRules/no-component-ref-set-input -- the typed wrapper around the string based API
        ref.setInput(publicName, value);
    }
}
