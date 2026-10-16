import { describe, expect, it } from 'vitest';
import { canEditMessageVisually, editorToMessage, messageToEditorHtml } from './message-editor-conversion';

describe('Message editor conversion', () => {
    const roundTrip = (message: string) => {
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(message);
        return editorToMessage(element);
    };

    it.each([
        'Hello **world** and *everyone*.',
        '<ins>Underlined</ins> and ~~removed~~',
        '[Documentation](https://example.org/docs)',
        '[user]Anna Müller(anna)[/user] and [user]Anna Müller(anna)[/user]',
        '[channel]general(42)[/channel]',
        '[programming]Exercise 1(/courses/1/exercises/2)[/programming]',
        '[lecture]Lecture 1(/courses/1/lectures/3)[/lecture]',
        '[slide]Slides 4(#123)[/slide]',
        '[faq]Question(/courses/1/faq?faqId=7)[/faq]',
        '![diagram](/api/core/files/attachments/diagram.png)',
        '`[user]Anna(anna)[/user]`',
        '```java\nSystem.out.println("hello");\n```',
        '- First\n- Second',
        '> A quotation',
        'First line\nSecond line',
        'Literal \\*stars\\* and \\[brackets\\]',
    ])('preserves the meaning of %s', (message) => {
        expect(roundTrip(message)).toBe(message);
    });

    it('renders references as protected readable items without exposing identifiers', () => {
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml('[user]Anna Müller(anna)[/user]');
        const reference = element.querySelector('[data-message-reference]')!;
        expect(reference.textContent).toBe('@Anna Müller');
        expect(reference.getAttribute('contenteditable')).toBe('false');
        expect(reference.getAttribute('data-message-reference')).toBe('[user]Anna Müller(anna)[/user]');
    });

    it('does not turn code examples into mention items', () => {
        expect(messageToEditorHtml('```\n[user]Anna(anna)[/user]\n```')).not.toContain('data-message-reference');
    });

    it('preserves legacy numeric message references', () => {
        expect(roundTrip('See #123.')).toBe('See #123.');
    });

    it('keeps rendered line breaks without adding visible HTML layout whitespace', () => {
        expect(messageToEditorHtml('One\nTwo\n\nThree')).toBe('<p>One<br>Two</p><p>Three</p>');
    });

    it('uses editable line-break elements for code instead of an invisible trailing newline', () => {
        expect(messageToEditorHtml('```java\na\nb\n```')).toBe('<pre><code class="language-java">a<br>b</code></pre>');
    });

    it('preserves nested ordered-list structure after editing', () => {
        const source = '1. a\n   1. nested\n   2. again\n2. b';
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(roundTrip(source));
        expect(element.querySelectorAll('ol > li > ol > li')).toHaveLength(2);
        expect(element.firstElementChild!.children).toHaveLength(2);
    });

    it('preserves an image title when another part of the message is edited', () => {
        expect(roundTrip('![diagram](/diagram.png "Caption")')).toBe('![diagram](/diagram.png "Caption")');
    });

    it.each(['| a | b |\n|---|---|\n| 1 | 2 |', '$$x^2$$', '<table><tr><td>Keep me</td></tr></table>', '<span style="color:red">Text</span>'])(
        'keeps unsupported legacy content in the Markdown editor: %s',
        (message) => {
            expect(canEditMessageVisually(message)).toBe(false);
        },
    );

    it('accepts supported formatting and literal code containing HTML', () => {
        expect(canEditMessageVisually('<ins>Text</ins> and `<span>`')).toBe(true);
    });

    it('does not allow executable URLs or HTML into the editing surface', () => {
        const html = messageToEditorHtml('<img src=x onerror=alert(1)> [bad](javascript:alert(1))');
        expect(html).not.toContain('onerror=');
        expect(html).not.toContain('href="javascript:');
    });
});
