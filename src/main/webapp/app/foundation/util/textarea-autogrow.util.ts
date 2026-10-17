/**
 * Resizes a textarea so that it exactly fits its content, growing and shrinking with the text.
 * The height is reset first so that a shortened text can shrink the textarea again. The border is added
 * because `scrollHeight` excludes it while a `border-box` textarea includes it in its `height`.
 * A `max-height` set in CSS still applies, so a textarea that outgrows it scrolls instead.
 *
 * @param textarea the textarea to resize
 */
export function autogrowTextarea(textarea: HTMLTextAreaElement): void {
    textarea.style.height = '0px';
    const borderHeight = textarea.offsetHeight - textarea.clientHeight;
    textarea.style.height = `${textarea.scrollHeight + borderHeight}px`;
}
