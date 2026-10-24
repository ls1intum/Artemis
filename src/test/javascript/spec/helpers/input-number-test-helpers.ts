import { InputNumber } from 'primeng/inputnumber';

/**
 * The decimal separator that the given PrimeNG number input accepts, which depends on the locale of the runtime.
 */
export function decimalSeparatorOf(inputNumber: InputNumber): string {
    return new Intl.NumberFormat(inputNumber.locale).formatToParts(1.1).find((part) => part.type === 'decimal')!.value;
}

/**
 * Replaces the content of a PrimeNG number input with the given text by typing it key by key, as a user does, and leaves the input.
 * PrimeNG validates every key press, so a character that the input does not allow is dropped.
 */
export function typeIntoInputNumber(input: HTMLInputElement, text: string): void {
    input.focus();
    input.setSelectionRange(0, input.value.length);
    for (const character of text) {
        const code = character.charCodeAt(0);
        const key = { key: character, charCode: code, keyCode: code, which: code, bubbles: true, cancelable: true };
        input.dispatchEvent(new KeyboardEvent('keydown', key));
        input.dispatchEvent(new KeyboardEvent('keypress', key));
    }
    input.dispatchEvent(new Event('blur'));
}
