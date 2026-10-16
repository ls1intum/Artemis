import MarkdownIt, { type RendererRule } from 'markdown-it';
import DOMPurify from 'dompurify';
import TurndownService from 'turndown';

// The label ends at the first opening parenthesis; excluding it prevents overlapping label/payload backtracking.
const referencePattern = /^\[(user|channel|programming|modeling|quiz|text|file-upload|lecture|lecture-unit|attachment|slide|faq)]([^\n(]*)\(([^\n]*?)\)\[\/\1]/;
const markdown = MarkdownIt({ html: true, breaks: true, linkify: true });

markdown.inline.ruler.before('html_inline', 'message_reference', (state, silent) => {
    const match = referencePattern.exec(state.src.slice(state.pos));
    if (!match) {
        return false;
    }
    if (!silent) {
        const token = state.push('message_reference', '', 0);
        const prefix = match[1] === 'user' ? '@' : match[1] === 'channel' ? '#' : '';
        token.content = referenceHtml(match[0], prefix + match[2], match[1]);
    }
    state.pos += match[0].length;
    return true;
});
markdown.renderer.rules.message_reference = (tokens, index) => tokens[index].content;

const renderCode: RendererRule = (tokens, index) => {
    const token = tokens[index];
    const language = token.info.trim().split(/\s+/)[0];
    const attribute = language ? ` class="language-${markdown.utils.escapeHtml(language)}"` : '';
    const code = markdown.utils.escapeHtml(token.content.replace(/\n$/, '')).replace(/\n/g, '<br>');
    return `<pre><code${attribute}>${code}</code></pre>`;
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
const escapeMarkdown = serializer.escape.bind(serializer);
// Turndown's defaults omit HTML, entities, and the strikethrough syntax supported by our renderer.
serializer.escape = (text) => escapeMarkdown(text).replace(/[<&~]/g, '\\$&');
serializer.addRule('message-reference', {
    filter: (node) => node.hasAttribute('data-message-reference'),
    replacement: (_content, node) => node.getAttribute('data-message-reference') ?? '',
});
serializer.addRule('message-underline', {
    filter: ['ins', 'u'],
    replacement: (content) => `<ins>${content}</ins>`,
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
    const escapedLabel = markdown.utils.escapeHtml(label);
    const image = imageSource ? `<img src="${markdown.utils.escapeHtml(imageSource)}" alt="${escapedLabel}">` : '';
    return `<span contenteditable="false" data-message-reference="${markdown.utils.escapeHtml(source)}" data-reference-type="${type}">${image}${escapedLabel}</span>`;
}

/** Reads code without the caret placeholders that are needed by native browser editing. */
export function editorCodeText(element: HTMLElement): string {
    const code = element.cloneNode(true) as HTMLElement;
    code.querySelectorAll('[data-message-placeholder]').forEach((placeholder) => placeholder.remove());
    code.querySelectorAll('br').forEach((lineBreak) => lineBreak.replaceWith('\n'));
    return code.textContent ?? '';
}

/** Limits the exploratory composer to syntax it can export without silently discarding legacy content. */
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
        return (token.children ?? []).every((child) => {
            if (child.type === 'html_inline') {
                return /^<\/?(?:ins|u)>$/.test(child.content);
            }
            return child.type !== 'text' || !/\$\$|\$\S[^$\n]*\$/.test(child.content);
        });
    });
}

/** Renders only the message grammar; reference payloads remain attached to protected inline items. */
export function messageToEditorHtml(source: string): string {
    // Preserve user whitespace in the editing host, but remove linefeeds used only to lay out generated HTML.
    // Literal code is escaped by markdown-it and cannot match these tag boundaries.
    const html = markdown.render(source).replace(/(<\/(?:p|li|ul|ol|blockquote|pre)>|<(?:ul|ol|blockquote|li)>|<br>)\n/g, '$1');
    return DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'ins', 'u', 's', 'del', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'a', 'span', 'img'],
        ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'start', 'class', 'contenteditable', 'data-message-reference', 'data-reference-type'],
        ALLOW_DATA_ATTR: false,
    });
}

/** Serializes the editable document to the existing Artemis message format. */
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
