/**
 * The Mutable Type overrides the Wrapped Types readonly annotations, making all properties writable.
 */
export type Mutable<T> = {
    -readonly [P in keyof T]: T[P];
};
