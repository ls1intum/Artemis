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
 * What each key of the passed object may hold: the write type of the input of that name, and nothing for any other key.
 * Checking every key of the argument, not only the ones the helper knows, also rejects an object that is stored in a
 * variable and carries an extra property; TypeScript only reports excess properties of an object literal. The `-?` makes
 * an optional property of such a variable a required one, so that it cannot smuggle in `undefined` for a required input.
 */
type AllowedValues<T, V> = { [K in keyof V]-?: K extends InputName<T> ? InputWriteType<T[K]> : never };

/** Whether `T` is a union: `T extends unknown` distributes over the members, and `[U] extends [T]` holds for a single one only. */
type IsUnion<T, U = T> = T extends unknown ? ([U] extends [T] ? false : true) : never;

/**
 * Sets inputs on a component that was created in code (`ViewContainerRef.createComponent`, `createComponent`, a portal).
 *
 * `ComponentRef.setInput` takes the input name as a plain string, so a misspelled or removed input is only logged as NG0303
 * at runtime (it does not throw), and only when that code path runs. This wrapper checks the names and the value types
 * against the component class at compile time. Every input that is passed needs a value of its declared type, so `undefined`
 * is only accepted by an input whose type includes it. Pass a plain object, not a class instance, and a ref to one component
 * type: a union of components is rejected, because its members can declare the same input differently. Use it instead of
 * calling `setInput` directly; `localRules/no-component-ref-set-input` enforces that.
 *
 * Inputs are named by the class member, as in a template binding to the component class. An input that is declared with an
 * `alias` is set under its alias. When the set of components is closed and known, declare them in a template with a
 * `@switch` instead, which the template compiler checks as well.
 *
 * @param ref The reference of the created component
 * @param values The inputs to set, by class member; omitted inputs keep their current value
 */
export function setInputs<T, V extends object>(ref: ComponentRef<T>, values: [IsUnion<T>] extends [true] ? never : V & AllowedValues<T, V>): void {
    // `setInput` looks an input up by its public (template) name, which differs from the class member for an aliased input.
    const declaredInputs = (ref.componentType ? reflectComponentType(ref.componentType)?.inputs : undefined) ?? [];
    for (const [name, value] of Object.entries(values)) {
        const publicName = declaredInputs.find((input) => input.propName === name)?.templateName ?? name;
        // This is one of the two places that may call `setInput` (the UI kit has its own copy); every caller is type checked.
        // eslint-disable-next-line localRules/no-component-ref-set-input -- the typed wrapper around the string based API
        ref.setInput(publicName, value);
    }
}
