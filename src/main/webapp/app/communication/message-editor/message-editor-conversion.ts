import MarkdownIt, { type RendererRule } from 'markdown-it';
import MarkdownItKatex from '@vscode/markdown-it-katex';
import DOMPurify from 'dompurify';
import TurndownService from 'turndown';

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

const serializer = new TurndownService({ bulletListMarker: '-', codeBlockStyle: 'fenced', emDelimiter: '*', strongDelimiter: '**' });
const underlineMarkdownStart = '<ins>';
const underlineMarkdownEnd = '</ins>';
const escapeMarkdown = serializer.escape.bind(serializer);
// Turndown first escapes backslashes and Markdown syntax. Keep those escape pairs intact while
// adding the HTML, entity, strikethrough, and math escapes that its defaults omit.
serializer.escape = (text) => escapeMarkdown(text).replace(/\\.|[<&~$]/gs, (token) => (token.length === 2 ? token : '\\' + token));
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
    replacement: (_content, node) => (node.hasAttribute('data-message-placeholder') ? '' : '\n'),
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

function referenceHtml(source: string, label: string, type: string, imageSource?: string): string {
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
    return reference.outerHTML;
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
    return DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'ins', 'u', 's', 'del', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'a', 'span', 'img'],
        ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'start', 'class', 'contenteditable', 'data-message-reference', 'data-reference-type'],
        ALLOW_DATA_ATTR: false,
        RETURN_DOM_FRAGMENT: true,
    });
}

/** Serializes the editable document, escaping literal text while retaining the original payloads of protected references. */
export function editorToMessage(element: HTMLElement): string {
    // Native editing uses divs for new paragraphs in some browsers. Turndown understands them as blocks.
    return serializer.turndown(element.innerHTML);
}

/** Accepts web, mail and relative Artemis links while rejecting executable schemes. */
export function isMessageLinkSafe(value: string): boolean {
    if (!value.trim() || Array.from(value).some((character) => character.charCodeAt(0) <= 32)) {
        return false;
    }
    try {
        return ['http:', 'https:', 'mailto:'].includes(new URL(value, 'https://artemis.invalid').protocol);
    } catch {
        return false;
    }
}
