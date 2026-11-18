/**
 * Replaces the content of a TUM AET UI number input with the given text by typing it character by character, as a
 * user does, and by default leaves the field. The input reformats its text after every character, so the typed text
 * is appended to what it shows. A character that the input does not allow is dropped by it.
 *
 * @param options.leave whether to leave the field afterwards, which applies the bounds of the input; pass `false` to
 * check what happens while the field is still focused
 */
export function typeIntoInputNumber(input: HTMLInputElement, text: string, options: { leave?: boolean } = {}): void {
    input.focus();
    input.value = '';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    for (const character of text) {
        input.value += character;
        input.setSelectionRange(input.value.length, input.value.length);
        input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    if (options.leave ?? true) {
        input.dispatchEvent(new Event('blur'));
        input.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    }
}
