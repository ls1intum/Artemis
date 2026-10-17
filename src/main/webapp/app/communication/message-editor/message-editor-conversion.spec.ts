import { describe, expect, it } from 'vitest';
import { canEditMessageVisually, editorToMessage, messageToEditorFragment } from './message-editor-conversion';
import { renderPostingMarkdownToHtml } from 'app/foundation/util/markdown-render.util';

/** Serializes the fragment only for assertions about generated markup. */
function messageToEditorHtml(source: string): string {
    const element = document.createElement('div');
    element.append(messageToEditorFragment(source));
    return element.innerHTML;
}

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

    it.each([
        '[attachment]Lecture slides(lecture/intro_(1).pdf)[/attachment]',
        '[lecture-unit]Slides(lecture/(draft)/(intro).pdf?version=3)[/lecture-unit]',
        '[user]Anna & "Friends" <em>anna</em>(anna)[/user]',
    ])('keeps reference payloads with parentheses and HTML-like labels intact: %s', (source) => {
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(source);
        expect(element.querySelectorAll('[data-message-reference]')).toHaveLength(1);
        expect(element.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe(source);
        expect(element.querySelector('em, img')).toBeNull();
        expect(editorToMessage(element)).toBe(source);
    });

    it.each([1024, 8192, 65536])('leaves an unterminated reference with %i opening parentheses as literal text', (length) => {
        const source = '[user]Anna(' + '('.repeat(length);
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(source);
        expect(element.querySelector('[data-message-reference]')).toBeNull();
        expect(element.textContent).toBe(source);
    });

    it('leaves a long sequence of unmatched opening brackets as literal text', () => {
        const source = '['.repeat(65536);
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(source);
        expect(element.querySelector('[data-message-reference]')).toBeNull();
        expect(element.textContent).toBe(source);
    });

    it('does not consume a following valid reference while recovering from a malformed reference on the preceding line', () => {
        const source = '[user]Anna(' + '('.repeat(1024) + '\n[attachment]Slides(lecture/intro_(1).pdf)[/attachment]';
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(source);
        expect(element.querySelectorAll('[data-message-reference]')).toHaveLength(1);
        expect(element.querySelector('[data-message-reference]')!.textContent).toBe('Slides');
        expect(element.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe('[attachment]Slides(lecture/intro_(1).pdf)[/attachment]');
    });

    it('keeps closing parentheses and other reference closing tags inside a reference payload', () => {
        const source = '[attachment]Slides(lecture/(draft)/intro).pdf?note=)[/slide])[/attachment]';
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(source);
        expect(element.querySelectorAll('[data-message-reference]')).toHaveLength(1);
        expect(element.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe(source);
        expect(editorToMessage(element)).toBe(source);
    });

    it('ends a reference at its first matching typed closing delimiter', () => {
        const reference = '[attachment]Slides(lecture/intro.pdf)[/attachment]';
        const suffix = 'extra)[/attachment]';
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(reference + suffix);
        expect(element.querySelectorAll('[data-message-reference]')).toHaveLength(1);
        expect(element.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe(reference);
        expect(element.textContent).toBe('Slides' + suffix);
    });

    it('does not consume across a newline when several malformed references precede a valid reference', () => {
        const malformed = '[user]Anna(anna [channel]general(42 [attachment]Slides(';
        const reference = '[attachment]Valid(lecture/intro.pdf)[/attachment]';
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(malformed + '\n' + reference);
        expect(element.querySelectorAll('[data-message-reference]')).toHaveLength(1);
        expect(element.querySelector('[data-message-reference]')!.getAttribute('data-message-reference')).toBe(reference);
        expect(element.firstElementChild!.textContent).toBe(malformed + 'Valid');
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

    it('previews an attached image inside a protected item while preserving its original source', () => {
        const source = '![diagram](/api/core/files/attachments/diagram.png)';
        const element = document.createElement('div');
        element.innerHTML = messageToEditorHtml(source);
        const image = element.querySelector('img')!;
        expect(image?.getAttribute('src')).toBe('/api/core/files/attachments/diagram.png');
        expect(image.getAttribute('alt')).toBe('diagram');
        expect(image.closest('[contenteditable="false"]')).not.toBeNull();
        expect(editorToMessage(element)).toBe(source);
    });

    it.each(['| a | b |\n|---|---|\n| 1 | 2 |', '$$x^2$$', '<table><tr><td>Keep me</td></tr></table>', '<span style="color:red">Text</span>'])(
        'keeps unsupported legacy content in the Markdown editor: %s',
        (message) => {
            expect(canEditMessageVisually(message)).toBe(false);
        },
    );

    it.each([
        '> [!NOTE]\n> Note body',
        '> [!TIP] A helpful title\n> Tip body',
        '> [!IMPORTANT]\n> Important body',
        '> [!WARNING]\n> Warning body',
        '> [!CAUTION]\n> Caution body',
        '> [!warning] A custom title\n> Warning body',
        '> \\[!WARNING]\n> Warning body',
        '> > [!WARNING]\n> > Nested warning',
        '- Item\n\n  > [!WARNING]\n  > Nested warning',
    ])('keeps alert semantics in the Markdown editor: %s', (message) => {
        expect(canEditMessageVisually(message)).toBe(false);
    });

    it.each(['`> [!WARNING]`', '```\n> [!WARNING]\n> Body\n```', '> Ordinary quotation mentioning [!WARNING]', '[!WARNING] outside a quote'])(
        'accepts literal alert examples and ordinary quotations: %s',
        (message) => {
            expect(canEditMessageVisually(message)).toBe(true);
        },
    );

    it.each([
        ['List<String>', 'List\\<String>'],
        ['<ins>literal</ins>', '\\<ins>literal\\</ins>'],
        ['&copy; &amp; &#169;', '\\&copy; \\&amp; \\&#169;'],
        ['~~literal~~ and ~single~', '\\~\\~literal\\~\\~ and \\~single\\~'],
        ['**stars** [brackets] and \\slash', '\\*\\*stars\\*\\* \\[brackets\\] and \\\\slash'],
    ])('exports typed literal text without changing its meaning: %s', (text, expected) => {
        const element = document.createElement('div');
        const paragraph = document.createElement('p');
        paragraph.textContent = text;
        element.append(paragraph);

        expect(editorToMessage(element)).toBe(expected);
        const reopened = document.createElement('div');
        reopened.innerHTML = messageToEditorHtml(expected);
        expect(reopened.textContent).toBe(text);
        expect(reopened.querySelector('ins, del, s, strong, a, string')).toBeNull();
        expect(editorToMessage(reopened)).toBe(expected);
    });

    it.each([
        '~~~literal~~~',
        '~~~~literal~~~~',
        String.raw`\<ins>literal\</ins> \&copy; \~~literal~~`,
        String.raw`\\<script>alert(1)</script> \\&amp; \\~~~literal~~~`,
        String.raw`\\\<&~ **stars** [brackets] _emphasis_`,
    ])('keeps backslashes and adjacent Markdown, HTML, and entity syntax literal: %s', (text) => {
        const element = document.createElement('div');
        const paragraph = document.createElement('p');
        paragraph.textContent = text;
        element.append(paragraph);

        const message = editorToMessage(element);
        const reopened = document.createElement('div');
        reopened.innerHTML = messageToEditorHtml(message);
        expect(reopened.textContent).toBe(text);
        expect(reopened.querySelector('ins, del, s, strong, em, a, script, pre')).toBeNull();
        expect(editorToMessage(reopened)).toBe(message);
    });

    it.each(['$5', '$x^2$', '$$x^2$$', String.raw`\$x^2\$`, String.raw`\\$x^2\\$`])(
        'keeps typed dollars literal when the saved posting is rendered and reopened: %s',
        async (text) => {
            const element = document.createElement('div');
            const paragraph = document.createElement('p');
            paragraph.textContent = text;
            element.append(paragraph);

            const message = editorToMessage(element);
            const posting = document.createElement('div');
            posting.innerHTML = await renderPostingMarkdownToHtml(message);
            expect(posting.textContent).toBe(text);
            expect(posting.querySelector('math, .katex')).toBeNull();
            expect(canEditMessageVisually(message)).toBe(true);

            const reopened = document.createElement('div');
            reopened.innerHTML = messageToEditorHtml(message);
            expect(reopened.textContent).toBe(text);
            expect(editorToMessage(reopened)).toBe(message);
        },
    );

    it.each(['`$x^2$`', '```text\n$x^2$\n```'])('leaves dollars inside code literal without escaping them: %s', async (source) => {
        const message = roundTrip(source);
        expect(message).toBe(source);
        const posting = document.createElement('div');
        posting.innerHTML = await renderPostingMarkdownToHtml(message);
        expect(posting.querySelector('code')!.textContent?.trimEnd()).toBe('$x^2$');
        expect(posting.querySelector('math, .katex')).toBeNull();
        expect(canEditMessageVisually(message)).toBe(true);
    });

    it.each(['$x^2$', String.raw`$x\$y$`, '`code` $x^2$'])('rejects active math for visual editing: %s', (source) => {
        expect(canEditMessageVisually(source)).toBe(false);
    });

    it.each(['$ x $', '$ x$', '$x $', '$x\ny$', '$**x**$', '&#65;$x$', '$a `b$`', '$a [label$](https://example.org)', '$a **b$**'])(
        'rejects formulas accepted by the posting renderer for visual editing: %s',
        async (source) => {
            const posting = document.createElement('div');
            posting.innerHTML = await renderPostingMarkdownToHtml(source);
            expect(posting.querySelector('.katex')).not.toBeNull();
            expect(canEditMessageVisually(source)).toBe(false);
        },
    );

    it.each(['$5', String.raw`\$x^2\$`, '&dollar;x^2&dollar;', '[user]Dollar $name$(anna)[/user]'])('accepts literal dollars without math: %s', (source) => {
        expect(canEditMessageVisually(source)).toBe(true);
    });

    it.each(['Costs $5 and $10', '$5 for `a` and $10 for b', 'id$x$', '$x$id', '_$x$_', String.raw`\\$x^2$`])(
        'keeps currency and other literal dollars editable when the posting renderer leaves them literal: %s',
        async (source) => {
            const posting = document.createElement('div');
            posting.innerHTML = await renderPostingMarkdownToHtml(source);
            expect(posting.querySelector('.katex')).toBeNull();
            expect(canEditMessageVisually(source)).toBe(true);
        },
    );

    it.each(['<img src=x onerror=alert(1)>', '&copy; &#169;', String.raw`\<script> \&copy;`, '$x^2$'])(
        'keeps underlined literal text safe through posting and reopening: %s',
        async (text) => {
            const element = document.createElement('div');
            const underline = document.createElement('ins');
            underline.textContent = text;
            element.append(underline);

            const message = editorToMessage(element);
            const posting = document.createElement('div');
            posting.innerHTML = await renderPostingMarkdownToHtml(message);
            expect(posting.querySelector('ins')!.textContent).toBe(text);
            expect(posting.querySelector('img, script, math, .katex')).toBeNull();
            expect(canEditMessageVisually(message)).toBe(true);

            const reopened = document.createElement('div');
            reopened.innerHTML = messageToEditorHtml(message);
            expect(reopened.querySelector('ins')!.textContent).toBe(text);
            expect(editorToMessage(reopened)).toBe(message);
        },
    );

    it('preserves protected reference source inside underline markup', () => {
        const source = '<ins>[user]Anna & <em>Friends</em>(anna)[/user]</ins>';
        expect(roundTrip(source)).toBe(source);
        expect(canEditMessageVisually(source)).toBe(true);
    });

    it('accepts supported formatting and literal code containing HTML', () => {
        expect(canEditMessageVisually('<ins>Text</ins> and `<span>`')).toBe(true);
    });

    it('does not allow executable URLs or HTML into the editing surface', () => {
        const html = messageToEditorHtml('<img src=x onerror=alert(1)> [bad](javascript:alert(1))');
        expect(html).not.toContain('onerror=');
        expect(html).not.toContain('href="javascript:');
    });
});
