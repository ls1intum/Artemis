/**
 * Typed wrapper around {@link JSON.parse}.
 *
 * `JSON.parse` is declared to return `any`, which silently disables type checking on everything derived
 * from its result — a typo such as `obj.colour` compiles and yields `undefined` at runtime. This wrapper
 * localizes that single `any` boundary: the generic defaults to `unknown`, so a caller that does not
 * supply the expected shape gets an `unknown` back and cannot access its properties without a compile
 * error. Always call it with an explicit type argument, e.g. `parseJson<MyDto>(text)`.
 *
 * This is a compile-time guard against typos and refactor drift, not runtime validation: the parsed value
 * is still whatever the input string contained. For untrusted or structurally complex payloads, validate
 * the result after parsing (e.g. against a schema).
 *
 * @param value the JSON string to parse
 * @returns the parsed value, typed as {@link T}
 * @throws SyntaxError if `value` is not valid JSON (same as {@link JSON.parse})
 */
export function parseJson<T = unknown>(value: string): T {
    // eslint-disable-next-line no-restricted-properties -- the single sanctioned JSON.parse call; this wrapper exists precisely to provide it with an explicit return type
    return JSON.parse(value);
}
