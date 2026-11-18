/** Input types in which Enter submits a form; checkboxes and buttons keep their own meaning of Enter. */
const SUBMITTING_INPUT_TYPES = new Set(['text', 'search', 'url', 'email', 'tel', 'password', 'number']);

/**
 * Whether Enter in the given element would submit the form around it, as in a single-line text field.
 * @param target the target of a keyboard event
 */
export function isSingleLineField(target: EventTarget | null): boolean {
    return target instanceof HTMLInputElement && SUBMITTING_INPUT_TYPES.has(target.type);
}
