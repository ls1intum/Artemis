import DOMPurify from 'dompurify';
import { parseJson } from 'app/foundation/util/json.util';
import { canEditMessageVisually, editorToMessage, isMessageLinkSafe, messageToEditorFragment, normalizeMessageLink } from './message-editor-conversion';

/** Versioned Markdown, rather than trusted HTML attributes, carries references between Artemis composers. */
export const MESSAGE_CLIPBOARD_TYPE = 'application/x-artemis-message+json';

/** Serializes a copied editor fragment without losing opaque references or boundary whitespace. */
export function encodeMessageClipboard(content: HTMLElement): string {
    const text = content.textContent ?? '';
    return JSON.stringify({ version: 1, markdown: editorToMessage(content), leading: boundaryWhitespace(text), trailing: boundaryWhitespace(text, true) });
}

/** Clipboard metadata is untrusted: validate its shape and supported wire format, then use the sanitized message parser. */
export function decodeMessageClipboard(serialized: string): DocumentFragment | undefined {
    if (!serialized) return undefined;
    try {
        const value = parseJson<unknown>(serialized);
        if (
            typeof value !== 'object' ||
            value === null ||
            !('version' in value) ||
            value.version !== 1 ||
            !('markdown' in value) ||
            typeof value.markdown !== 'string' ||
            !('leading' in value) ||
            typeof value.leading !== 'string' ||
            !('trailing' in value) ||
            typeof value.trailing !== 'string' ||
            boundaryWhitespace(value.leading) !== value.leading ||
            boundaryWhitespace(value.trailing) !== value.trailing ||
            !canEditMessageVisually(value.markdown)
        ) {
            return undefined;
        }
        return restoreClipboardWhitespace(messageToEditorFragment(value.markdown), value.leading, value.trailing);
    } catch {
        return undefined;
    }
}

const canonicalTags: Partial<Record<string, string>> = { B: 'strong', I: 'em', U: 'ins', DEL: 's', STRIKE: 's', DIV: 'p' };

const semanticMarks: Partial<Record<string, string>> = { B: 'strong', STRONG: 'strong', I: 'em', EM: 'em', U: 'ins', INS: 'ins', S: 's', DEL: 's', STRIKE: 's' };
const blockTags = 'p, div, ul, ol, li, blockquote, pre, h1, h2, h3, h4, h5, h6';

/** Converts untrusted clipboard HTML to the editor's existing message formats, or requests a plain-text fallback. */
export function clipboardToEditorFragment(html: string): DocumentFragment | undefined {
    if (!html.trim()) return undefined;
    // This fragment is never attached to the live document. Read only supported CSS marks, then discard all source styles.
    const fragment = DOMPurify.sanitize(html, {
        ALLOWED_TAGS: [
            'p',
            'div',
            'br',
            'strong',
            'b',
            'em',
            'i',
            'ins',
            'u',
            's',
            'strike',
            'del',
            'code',
            'pre',
            'blockquote',
            'ul',
            'ol',
            'li',
            'a',
            'span',
            'h1',
            'h2',
            'h3',
            'h4',
            'h5',
            'h6',
            'table',
        ],
        ALLOWED_ATTR: ['href', 'start', 'style', 'data-message-placeholder'],
        ALLOW_DATA_ATTR: false,
        ALLOW_ARIA_ATTR: false,
        RETURN_DOM_FRAGMENT: true,
    });
    // The composer has no table model. The clipboard's plain text retains its row/cell separators.
    if (fragment.querySelector('table') || !fragment.textContent?.trim()) return undefined;
    fragment.querySelectorAll('br[data-message-placeholder]').forEach((placeholder) => placeholder.remove());
    for (const element of Array.from(fragment.querySelectorAll<HTMLElement>('*')).reverse()) {
        const style = element.style;
        const weight = style.fontWeight;
        const semanticMark = semanticMarks[element.tagName];
        const containsBlocks = element.querySelector(blockTags) !== null;
        const marks = [
            ...(weight === 'bold' || weight === 'bolder' || Number(weight) >= 600 ? ['strong'] : []),
            ...(style.fontStyle === 'italic' || style.fontStyle.startsWith('oblique') ? ['em'] : []),
            ...((style.textDecorationLine || style.textDecoration).includes('underline') ? ['ins'] : []),
            ...((style.textDecorationLine || style.textDecoration).includes('line-through') ? ['s'] : []),
        ];
        for (const attribute of Array.from(element.attributes)) {
            if (!(element.matches('a') && attribute.name === 'href') && !(element.matches('ol') && attribute.name === 'start')) {
                element.removeAttribute(attribute.name);
            }
        }
        applyClipboardMarks(
            element,
            marks.filter((mark) => mark !== semanticMark),
        );
        if (element.matches('a')) {
            const href = normalizeMessageLink(element.getAttribute('href') ?? '');
            if (isMessageLinkSafe(href, element.textContent ?? '')) {
                element.setAttribute('href', href);
            } else {
                element.replaceWith(...Array.from(element.childNodes));
            }
        } else if (element.matches('h1, h2, h3, h4, h5, h6')) {
            const paragraph = element.ownerDocument.createElement('p');
            const strong = element.ownerDocument.createElement('strong');
            strong.append(...Array.from(element.childNodes));
            paragraph.append(strong);
            element.replaceWith(paragraph);
        } else if (element.matches('b, strong') && weight && (weight === 'normal' || Number(weight) < 600)) {
            // Google Docs wraps copied content in a normal-weight <b>; only its explicitly marked descendants are bold.
            element.replaceWith(...Array.from(element.childNodes));
        } else if (semanticMark !== undefined && containsBlocks) {
            applyClipboardMarks(element, [semanticMark]);
            element.replaceWith(...Array.from(element.childNodes));
        } else if (element.tagName === 'SPAN' || (element.tagName === 'DIV' && containsBlocks)) {
            element.replaceWith(...Array.from(element.childNodes));
        } else {
            const tag = canonicalTags[element.tagName];
            if (tag) {
                const canonical = element.ownerDocument.createElement(tag);
                canonical.append(...Array.from(element.childNodes));
                element.replaceWith(canonical);
            }
        }
    }
    // Repeated semantic/CSS marks must not serialize as literal doubled Markdown delimiters.
    for (const mark of Array.from(fragment.querySelectorAll<HTMLElement>('strong, em, ins, s, code'))) {
        if (mark.parentElement?.closest(mark.tagName.toLowerCase())) mark.replaceWith(...Array.from(mark.childNodes));
    }
    const container = document.createElement('div');
    container.append(fragment);
    const message = editorToMessage(container);
    if (!canEditMessageVisually(message)) return undefined;
    // Canonicalize mixed inline/block wrappers through the existing wire format before inserting into a paragraph.
    const canonical = messageToEditorFragment(message);
    // Serializing an isolated fragment trims boundary spaces. Restore only the missing horizontal whitespace so a
    // formatted word cannot join the surrounding text; code-span padding that survived the round trip stays unchanged.
    const text = container.textContent ?? '';
    return restoreClipboardWhitespace(canonical, boundaryWhitespace(text), boundaryWhitespace(text, true));
}

function restoreClipboardWhitespace(canonical: DocumentFragment, leading: string, trailing: string): DocumentFragment {
    const first = canonical.firstElementChild;
    const last = canonical.lastElementChild;
    if (first?.matches('p')) {
        const retained = boundaryWhitespace(first.textContent ?? '').length;
        if (leading.length > retained) first.prepend(leading.slice(retained));
    }
    if (last?.matches('p')) {
        const retained = boundaryWhitespace(last.textContent ?? '', true).length;
        if (trailing.length > retained) last.append(trailing.slice(retained));
    }
    return canonical;
}

/** Scans either edge once; an unanchored trailing-whitespace regex can backtrack across a long pasted space run. */
function boundaryWhitespace(text: string, trailing = false): string {
    let length = 0;
    while (length < text.length) {
        const character = text[trailing ? text.length - length - 1 : length];
        if (character !== ' ' && character !== '\t') break;
        length++;
    }
    return trailing ? text.slice(text.length - length) : text.slice(0, length);
}

/** Applies document-level text marks to inline runs without putting wrappers between a list and its items. */
function applyClipboardMarks(element: HTMLElement, marks: string[]): void {
    if (!marks.length || element.matches('pre, code')) return;
    let run: Node[] = [];
    const flush = () => {
        if (!run.length) return;
        const outer = element.ownerDocument.createElement(marks[0]);
        let inner = outer;
        for (const mark of marks.slice(1)) {
            const child = element.ownerDocument.createElement(mark);
            inner.append(child);
            inner = child;
        }
        element.insertBefore(outer, run[0]);
        inner.append(...run);
        run = [];
    };
    for (const child of Array.from(element.childNodes)) {
        if (child instanceof HTMLElement && child.matches(blockTags)) {
            flush();
            applyClipboardMarks(child, marks);
        } else {
            run.push(child);
        }
    }
    flush();
}
