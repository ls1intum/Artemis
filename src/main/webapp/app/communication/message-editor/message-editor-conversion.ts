import MarkdownIt, { type RendererRule } from 'markdown-it';
import MarkdownItKatex from '@vscode/markdown-it-katex';
import DOMPurify from 'dompurify';
import TurndownService from 'turndown';

// A literal numeric HTML entity remains text under the editor's escaping contract.
const numericPostReference = /(?<!&)#\d+/g;

const referenceTypes = ['user', 'channel', 'programming', 'modeling', 'quiz', 'text', 'file-upload', 'lecture', 'lecture-unit', 'attachment', 'slide', 'faq'];
// Classify formulas with the same existing grammar as the posting renderer. Duplicating
// delimiter detection misses math spanning formatting/code and can lock out ordinary currency.
const markdown = MarkdownIt({ html: true, breaks: true, linkify: true }).use(MarkdownItKatex, { enableMathInlineInHtml: true });

markdown.inline.ruler.before('html_inline', 'message_reference', (state, silent) => {
    if (state.src[state.pos] !== '[') {
        return false;
    }
    const type = referenceTypes.find((candidate) => state.src.startsWith(`[${candidate}]`, state.pos));
    if (!type) {
        return false;
    }
    const typeEnd = state.pos + type.length + 1;
    // A label ends at its first opening parenthesis. Payloads may contain parentheses and
    // other reference tags; only the matching typed closing delimiter ends the reference.
    const labelEnd = state.src.indexOf('(', typeEnd + 1);
    if (labelEnd === -1) {
        return false;
    }
    const closing = `)[/${type}]`;
    const payloadEnd = state.src.indexOf(closing, labelEnd + 1);
    const lineEnd = state.src.indexOf('\n', typeEnd + 1);
    if (payloadEnd === -1 || (lineEnd !== -1 && payloadEnd >= lineEnd)) {
        return false;
    }
    const end = payloadEnd + closing.length;
    if (!silent) {
        const token = state.push('message_reference', '', 0);
        const prefix = type === 'user' ? '@' : type === 'channel' ? '#' : '';
        token.content = referenceHtml(state.src.slice(state.pos, end), prefix + state.src.slice(typeEnd + 1, labelEnd), type);
    }
    state.pos = end;
    return true;
});
markdown.renderer.rules.message_reference = (tokens, index) => tokens[index].content;

// Markdown keeps escaped/entity characters distinct until text_join. Retain literal hashes before
// that distinction is lost, so the DOM reference scan cannot promote them to active post references.
// Formatting tokens can separate a hash from its digits until a later edit removes the formatting.
markdown.core.ruler.before('text_join', 'message_literal_hash', (state) => {
    for (const block of state.tokens) {
        const children = block.children ?? [];
        for (const [index, token] of children.entries()) {
            if (token.type === 'text_special' && token.content === '#') {
                let next = index + 1;
                while (
                    children[next] &&
                    (children[next].nesting !== 0 ||
                        (children[next].type === 'text' && !children[next].content) ||
                        (children[next].type === 'html_inline' && /^<\/?(?:ins|u)>$/.test(children[next].content)))
                ) {
                    next++;
                }
                if (/^\d/.test(children[next]?.content ?? '')) token.type = 'message_literal_hash';
            }
        }
    }
});
markdown.renderer.rules.message_literal_hash = () => '<span data-message-literal="#">#</span>';

const renderCode: RendererRule = (tokens, index) => {
    const token = tokens[index];
    const language = token.info.trim().split(/\s+/)[0];
    const pre = document.createElement('pre');
    const code = document.createElement('code');
    if (language) {
        code.className = `language-${language}`;
    }
    token.content
        .replace(/\n$/, '')
        .split('\n')
        .forEach((line, lineNumber) => {
            if (lineNumber) {
                code.append(document.createElement('br'));
            }
            code.append(document.createTextNode(line));
        });
    pre.append(code);
    return pre.outerHTML;
};
markdown.renderer.rules.fence = renderCode;
markdown.renderer.rules.code_block = renderCode;

markdown.renderer.rules.image = (tokens, index) => {
    const token = tokens[index];
    const label = token.content;
    const source = String(token.attrGet('src') ?? '');
    const title = token.attrGet('title');
    return referenceHtml(`![${label}](${source}${title ? ' ' + JSON.stringify(String(title)) : ''})`, label || source, 'image', source);
};

// Markdown has no empty line that survives between two lines: a blank line only separates paragraphs and further blank lines
// collapse into it. The first empty line of a run is therefore written as that separator, and each further one as a paragraph
// holding a non-breaking space, which every Markdown renderer shows as a blank line.
const emptyLineParagraph = '&nbsp;\n\n';
const serializer = new TurndownService({
    bulletListMarker: '-',
    codeBlockStyle: 'fenced',
    emDelimiter: '*',
    strongDelimiter: '**',
    // Turndown drops blank blocks. Keep the paragraph of a further empty line, so reopening and saving a sent message retains it.
    blankReplacement: (_content, node) => {
        if (node.nodeName === 'P' && /^\u00a0+$/.test(node.textContent ?? '') && !node.closest('li')) {
            return emptyLineParagraph;
        }
        // Turndown marks the nodes it visits, which its typings leave out
        return (node as HTMLElement & { isBlock?: boolean }).isBlock ? '\n\n' : '';
    },
});
const underlineMarkdownStart = '<ins>';
const underlineMarkdownEnd = '</ins>';
const escapeMarkdown = serializer.escape.bind(serializer);
// Turndown escapes "# ", "+ " and "1. " only when a space follows, but typed text that ends right after the marker still
// starts a heading or a list (a lone "#" is an empty heading, which the editor cannot edit). Escape those markers too.
const loneBlockMarker = /^(?:(#{1,6}|\+)$|(\d+)([.)])(?=$| ))/;
const escapeBlockMarker = (text: string) =>
    text.replace(loneBlockMarker, (_match, symbol?: string, digits?: string, delimiter?: string) => (symbol ? '\\' + symbol : `${digits}\\${delimiter}`));
// Turndown first escapes backslashes and Markdown syntax. Keep those escape pairs intact while
// adding the HTML, entity, strikethrough, and math escapes that its defaults omit.
serializer.escape = (text) => escapeBlockMarker(escapeMarkdown(text)).replace(/\\.|[<&~$]/gs, (token) => (token.length === 2 ? token : '\\' + token));
serializer.addRule('message-literal-hash', {
    filter: (node) => node.hasAttribute('data-message-literal'),
    // Retain existing escapes when editing grows the literal marker beyond its original single hash.
    replacement: (content) => content.replace(/\\.|#/gs, (token) => (token.length === 2 ? token : '\\#')),
});
serializer.addRule('message-reference', {
    filter: (node) => node.hasAttribute('data-message-reference'),
    replacement: (_content, node) => node.getAttribute('data-message-reference') ?? '',
});
serializer.addRule('message-underline', {
    filter: ['ins', 'u'],
    // These fixed delimiters belong to the message's Markdown wire grammar. Turndown
    // has already serialized content; HTML encoding would corrupt its escapes and reference payloads.
    replacement: (content) => [underlineMarkdownStart, content, underlineMarkdownEnd].join(''),
});
serializer.addRule('message-strikethrough', {
    filter: ['s', 'del'],
    replacement: (content) => `~~${content}~~`,
});
serializer.addRule('message-line-break', {
    filter: 'br',
    replacement: (_content, node) => {
        if (node.hasAttribute('data-message-placeholder')) {
            return '';
        }
        // A blank line inside a list item would turn the whole list loose, which changes the spacing of its other items
        if (node.closest('li')) {
            return '\n';
        }
        let precedingBreaks = 0;
        for (let sibling = node.previousSibling; sibling instanceof HTMLBRElement && !sibling.hasAttribute('data-message-placeholder'); sibling = sibling.previousSibling) {
            precedingBreaks++;
        }
        return precedingBreaks === 0 ? '\n' : precedingBreaks === 1 ? '\n\n' : emptyLineParagraph;
    },
});
serializer.addRule('message-inline-code', {
    filter: (node) => node.nodeName === 'CODE' && node.parentNode?.nodeName !== 'PRE',
    // A Markdown code span cannot contain a line break, and Turndown would merge the lines into one. Write one span per
    // editor line instead, so the sent message shows the same separate lines as the editor.
    replacement: (_content, node) => editorCodeText(node).split('\n').map(inlineCodeSpan).join('\n'),
});
serializer.addRule('message-code-block', {
    filter: 'pre',
    replacement: (_content, node) => {
        const code = node.querySelector('code') ?? node;
        const content = editorCodeText(code);
        const language = /(?:^|\s)language-(\S+)/.exec(code.className)?.[1] ?? '';
        const fence = '`'.repeat(Math.max(3, ...Array.from(content.matchAll(/`+/g), (match) => match[0].length + 1)));
        return `\n\n${fence}${language}\n${content}\n${fence}\n\n`;
    },
});
serializer.addRule('message-list-item', {
    filter: 'li',
    replacement: (content, node) => {
        const parent = node.parentNode as HTMLElement;
        const marker = parent.nodeName === 'OL' ? `${Array.prototype.indexOf.call(parent.children, node) + Number(parent.getAttribute('start') ?? 1)}. ` : '- ';
        return marker + content.trim().replace(/\n/g, '\n' + ' '.repeat(marker.length)) + '\n';
    },
});

/** Mirrors Turndown's code span rule for a single line: pads backticks at the edges and lengthens the delimiter as needed. */
function inlineCodeSpan(line: string): string {
    if (!line) {
        return '';
    }
    const padding = /^`|^ .*?[^ ].* $|`$/.test(line) ? ' ' : '';
    const backtickRuns: string[] = line.match(/`+(?!`)/g) ?? [];
    let delimiter = '`';
    while (backtickRuns.includes(delimiter)) {
        delimiter += '`';
    }
    return delimiter + padding + line + padding + delimiter;
}

function referenceHtml(source: string, label: string, type: string, imageSource?: string): string {
    return referenceElement(source, label, type, imageSource).outerHTML;
}

function referenceElement(source: string, label: string, type: string, imageSource?: string): HTMLSpanElement {
    const reference = document.createElement('span');
    reference.setAttribute('contenteditable', 'false');
    reference.setAttribute('data-message-reference', source);
    reference.setAttribute('data-reference-type', type);
    if (imageSource) {
        const image = document.createElement('img');
        image.setAttribute('src', imageSource);
        image.alt = label;
        reference.append(image);
    }
    reference.append(document.createTextNode(label));
    return reference;
}

/** Protects numeric post references during loading or explicit formatting, without interrupting native numeric typing. */
export function protectMessagePostReferences(root: HTMLElement | DocumentFragment): void {
    const walker = root.ownerDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const texts: Text[] = [];
    let node: Node | null;
    while ((node = walker.nextNode())) {
        if (
            !node.parentElement?.closest('pre, code, a, [data-message-reference], [data-message-literal]') &&
            Array.from((node.textContent ?? '').matchAll(numericPostReference)).length > 0
        ) {
            texts.push(node as Text);
        }
    }
    for (const text of texts) {
        const fragment = root.ownerDocument.createDocumentFragment();
        let offset = 0;
        for (const match of text.data.matchAll(numericPostReference)) {
            fragment.append(text.data.slice(offset, match.index), referenceElement(match[0], match[0], 'post'));
            offset = match.index + match[0].length;
        }
        fragment.append(text.data.slice(offset));
        text.replaceWith(fragment);
    }
}

/** Splits inline wrappers around atomic references because the posting renderer splits references before rendering Markdown. */
export function splitProtectedMessageMarks(root: HTMLElement | DocumentFragment): void {
    for (const reference of Array.from(root.querySelectorAll<HTMLElement>('[data-message-reference]'))) {
        let parent = reference.parentElement;
        while (parent?.matches('strong, b, em, i, ins, u, s, del, code')) {
            const following = reference.ownerDocument.createRange();
            following.selectNodeContents(parent);
            following.setStartAfter(reference);
            const after = parent.cloneNode(false) as HTMLElement;
            after.append(following.extractContents());
            parent.after(reference, after);
            // Selection boundary spans must survive until the caller restores its selection.
            if (!parent.hasChildNodes()) parent.remove();
            if (!after.hasChildNodes()) after.remove();
            parent = reference.parentElement;
        }
    }
}

/** Reads code without the caret placeholders that are needed by native browser editing. */
export function editorCodeText(element: HTMLElement): string {
    const code = element.cloneNode(true) as HTMLElement;
    code.querySelectorAll('[data-message-placeholder]').forEach((placeholder) => placeholder.remove());
    code.querySelectorAll('br').forEach((lineBreak) => lineBreak.replaceWith('\n'));
    return code.textContent ?? '';
}

/**
 * Reports whether the editor can preserve the message's formatting when it serializes an edit.
 * Callers must retain unsupported source and block saving it through the rich editor; sanitizing it alone can discard formatting.
 */
export function canEditMessageVisually(source: string): boolean {
    let quoteDepth = 0;
    return markdown.parse(source, {}).every((token) => {
        if (token.type === 'blockquote_open') quoteDepth++;
        if (token.type === 'blockquote_close') quoteDepth--;
        // Match the legacy alert renderer, including its case-insensitive markers, titles, and escaped opening brackets.
        if (quoteDepth && token.type === 'inline' && /^\\?\[!(?:NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]/i.test(token.content)) {
            return false;
        }
        if (['table_open', 'heading_open', 'hr', 'html_block'].includes(token.type)) {
            return false;
        }
        if (token.type.startsWith('math_')) {
            return false;
        }
        return (token.children ?? []).every((child) => {
            if (child.type.startsWith('math_')) return false;
            if (child.type === 'html_inline') {
                return /^<\/?(?:ins|u)>$/.test(child.content);
            }
            return true;
        });
    });
}

/**
 * Renders a sanitized editing document after the caller has checked canEditMessageVisually.
 * Protected inline items retain their original reference payloads. The returned fragment is consumed when inserted into the host.
 */
export function messageToEditorFragment(source: string): DocumentFragment {
    // Preserve user whitespace in the editing host, but remove linefeeds used only to lay out generated HTML.
    // Literal code is escaped by markdown-it and cannot match these tag boundaries.
    const html = markdown.render(source).replace(/(<\/(?:p|li|ul|ol|blockquote|pre)>|<(?:ul|ol|blockquote|li)>|<br>)\n/g, '$1');
    const fragment = DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'ins', 'u', 's', 'del', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'a', 'span', 'img'],
        ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'start', 'class', 'contenteditable', 'data-message-reference', 'data-reference-type', 'data-message-literal'],
        ALLOW_DATA_ATTR: false,
        RETURN_DOM_FRAGMENT: true,
    });
    fragment.normalize();
    protectMessagePostReferences(fragment);
    splitProtectedMessageMarks(fragment);
    return fragment;
}

/** Serializes the editable document, escaping literal text while retaining the original payloads of protected references. */
export function editorToMessage(element: HTMLElement): string {
    // Native editing uses divs for new paragraphs in some browsers. Turndown understands them as blocks.
    // Work on a copy: native input and IME must retain their editable DOM while numeric references are still being typed.
    const content = element.cloneNode(true) as HTMLElement;
    // Managed character insertion can retain adjacent text nodes within one mark; match the complete visible token.
    content.normalize();
    protectMessagePostReferences(content);
    splitProtectedMessageMarks(content);
    // Empty lines before the first and after the last line carry nothing to separate
    return serializer
        .turndown(content.innerHTML)
        .replace(/^(?:&nbsp;\n\n)+/, '')
        .replace(/(?:\n\n&nbsp;)+$/, '');
}

/** Treat bare external hostnames as HTTPS while retaining explicitly relative Artemis links. */
export function normalizeMessageLink(value: string): string {
    const trimmed = value.trim();
    let end = trimmed.length;
    for (const delimiter of ['/', '?', '#']) {
        const position = trimmed.indexOf(delimiter);
        if (position >= 0) end = Math.min(end, position);
    }
    const [hostname, port, ...extra] = trimmed.slice(0, end).split(':');
    const labels = hostname.split('.');
    // Check one Unicode code point at a time. No input-length-dependent regex repetition or backtracking is needed.
    const bareHost = labels.length > 1 && labels.every((label) => label.length > 0 && Array.from(label).every((character) => /^[\p{L}\p{N}-]$/u.test(character)));
    const numericPort = port === undefined || (port.length > 0 && Array.from(port).every(isAsciiDigit));
    return bareHost && numericPort && !extra.length ? `https://${trimmed}` : trimmed;
}

function isAsciiDigit(character: string): boolean {
    return character >= '0' && character <= '9';
}

function containsPostReference(value: string): boolean {
    for (let index = 0; index + 1 < value.length; index++) {
        if (value[index] === '#' && isAsciiDigit(value[index + 1])) return true;
    }
    return false;
}

/** Accepts safe destinations and labels that the posting renderer can retain as one link. */
export function isMessageLinkSafe(value: string, label = ''): boolean {
    if (!value.trim() || containsPostReference(value) || containsPostReference(label) || Array.from(value).some((character) => character.charCodeAt(0) <= 32)) {
        return false;
    }
    try {
        return ['http:', 'https:', 'mailto:'].includes(new URL(value, 'https://artemis.invalid').protocol);
    } catch {
        return false;
    }
}
