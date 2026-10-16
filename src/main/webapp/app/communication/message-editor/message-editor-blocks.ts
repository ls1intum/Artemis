import { preserveMessageSelection, restoreMessageSelectionBoundaries } from './message-editor-selection';

type BlockStyle = 'ul' | 'ol' | 'blockquote' | 'pre';
interface Frame {
    tag: string;
    source: HTMLElement;
    ordinal?: number;
}
interface BlockRun {
    source: HTMLElement;
    nodes: Node[];
    frames: Frame[];
    code: boolean;
    selected: boolean;
}

/** Converts selected block runs, splitting list/quote containers rather than nesting a second style inside them. */
export function formatMessageBlocks(root: HTMLElement, selection: Range, style: BlockStyle): Range {
    const restore = preserveMessageSelection(root, selection);
    selection = restoreMessageSelectionBoundaries(root, false);
    const runs: BlockRun[] = [];
    const collect = (parent: HTMLElement, frames: Frame[]) => {
        let inline: Node[] = [];
        const emit = (source: HTMLElement, nodes: Node[], code = false) => runs.push({ source, nodes, frames, code, selected: false });
        const flush = () => {
            if (inline.length) {
                emit(parent, inline);
                inline = [];
            }
        };
        for (const node of Array.from(parent.childNodes)) {
            if (!(node instanceof HTMLElement) || !node.matches('p, div, ul, ol, blockquote, pre, li')) {
                inline.push(node);
            } else {
                flush();
                const tag = node.tagName.toLowerCase();
                if (tag === 'ul' || tag === 'ol' || tag === 'blockquote') {
                    collect(node, [...frames, { tag, source: node }]);
                } else if (tag === 'li') {
                    const list = frames.at(-1);
                    const itemFrames =
                        list && ['ul', 'ol'].includes(list.tag)
                            ? [
                                  ...frames.slice(0, -1),
                                  { tag: list.tag, source: list.source, ordinal: Array.prototype.indexOf.call(parent.children, node) + Number(parent.getAttribute('start') ?? 1) },
                              ]
                            : frames;
                    let body: Node[] = [];
                    let continuation = false;
                    const emitBody = () => {
                        if (body.length || !continuation) {
                            runs.push({
                                source: node,
                                nodes: body,
                                frames: continuation ? [...itemFrames, { tag: 'li', source: node }] : itemFrames,
                                code: false,
                                selected: false,
                            });
                            body = [];
                        }
                    };
                    for (const child of Array.from(node.childNodes)) {
                        if (child instanceof HTMLElement && child.matches('ul, ol')) {
                            emitBody();
                            collect(child, [...itemFrames, { tag: 'li', source: node }, { tag: child.tagName.toLowerCase(), source: child }]);
                            continuation = true;
                        } else {
                            body.push(child);
                        }
                    }
                    if (body.length) emitBody();
                } else if (tag === 'div' && node.querySelector('p, div, ul, ol, blockquote, pre')) {
                    collect(node, frames);
                } else {
                    emit(node, Array.from((tag === 'pre' ? (node.querySelector('code') ?? node) : node).childNodes), tag === 'pre');
                }
            }
        }
        flush();
    };
    collect(root, []);
    for (let index = runs.length - 1; index >= 0 && runs.length > 1; index--) {
        const run = runs[index];
        if (
            (run.source === root || run.source.matches('ul, ol, blockquote')) &&
            run.nodes.every(
                (node) => (node instanceof HTMLElement && node.hasAttribute('data-message-selection-boundary')) || (node.nodeType === Node.TEXT_NODE && !node.textContent),
            )
        ) {
            if (runs[index + 1]) runs[index + 1].nodes.unshift(...run.nodes);
            else runs[index - 1].nodes.push(...run.nodes);
            runs.splice(index, 1);
        }
    }

    const contains = (run: BlockRun, node: Node) =>
        (run.source !== root && run.source.tagName !== 'LI' && run.source.contains(node)) ||
        run.nodes.some((owned) => owned === node || owned.contains(node)) ||
        (run.source === node && run.nodes.includes(node.childNodes[selection.startOffset - 1]));
    if (selection.collapsed) {
        const current = runs.findLast((run) => contains(run, selection.startContainer)) ?? runs.at(-1);
        if (current) {
            current.selected = true;
        }
    } else {
        const intersects = (node: Node): boolean => {
            if (node instanceof HTMLElement && node.hasAttribute('data-message-selection-boundary')) {
                return false;
            }
            if (node.nodeType === Node.TEXT_NODE && !node.textContent) return false;
            if (node.nodeType === Node.TEXT_NODE || node instanceof HTMLBRElement) {
                const content = root.ownerDocument.createRange();
                if (node.nodeType === Node.TEXT_NODE) {
                    content.selectNodeContents(node);
                } else {
                    content.selectNode(node);
                }
                return content.compareBoundaryPoints(Range.END_TO_START, selection) < 0 && content.compareBoundaryPoints(Range.START_TO_END, selection) > 0;
            }
            return Array.from(node.childNodes).some(intersects);
        };
        runs.forEach((run) => (run.selected = run.nodes.some(intersects)));
    }
    const selected = runs.filter((run) => run.selected);
    const currentStyle = (run: BlockRun) => (run.code ? 'pre' : run.frames.at(-1)?.tag);
    const remove = selected.length > 0 && selected.every((run) => currentStyle(run) === style);
    const replacement: Frame = { tag: style, source: root.ownerDocument.createElement(style) };
    const fragment = root.ownerDocument.createDocumentFragment();
    const stack: { frame: Frame; element: HTMLElement }[] = [];
    const rendered = new Map<HTMLElement, HTMLElement>();
    let precedingCode: HTMLElement | undefined;

    const cloneContent = (nodes: Node[], plain: boolean, flattenParagraphs: boolean): DocumentFragment => {
        const content = root.ownerDocument.createDocumentFragment();
        for (const node of nodes) {
            if (node.nodeType === Node.TEXT_NODE && plain) {
                (node.textContent ?? '').split('\n').forEach((text, index) => {
                    if (index) content.append(root.ownerDocument.createElement('br'));
                    content.append(root.ownerDocument.createTextNode(text));
                });
            } else if (
                node instanceof HTMLElement &&
                !node.hasAttribute('data-message-reference') &&
                !node.hasAttribute('data-message-selection-boundary') &&
                ((plain && node.tagName !== 'BR') || (flattenParagraphs && node.matches('p, div')))
            ) {
                if (content.childNodes.length && node.matches('p, div')) {
                    content.append(root.ownerDocument.createElement('br'), root.ownerDocument.createElement('br'));
                }
                content.append(cloneContent(Array.from(node.childNodes), plain, flattenParagraphs));
            } else if (!(node instanceof HTMLElement && node.hasAttribute('data-message-placeholder'))) {
                content.append(node.cloneNode(true));
            }
        }
        return content;
    };

    // Keep untouched structures verbatim; rebuilding an unrelated list can change loose/nested item ordering.
    const untouched = new Set(Array.from(root.children).filter((child) => !runs.some((run) => run.selected && (run.source === child || child.contains(run.source)))));
    const retained = new Set<Element>();
    for (const run of runs) {
        let top: Element = run.source;
        while (top.parentElement && top.parentElement !== root) top = top.parentElement;
        if (untouched.has(top)) {
            if (!retained.has(top)) fragment.append(top.cloneNode(true));
            retained.add(top);
            stack.length = 0;
            precedingCode = undefined;
            continue;
        }
        const target = run.selected ? (remove ? 'p' : style) : run.code ? 'pre' : 'p';
        const enclosing = run.frames.slice(
            0,
            Math.max(
                0,
                run.frames.findLastIndex((frame) => ['ul', 'ol', 'blockquote'].includes(frame.tag)),
            ),
        );
        const frames = run.selected ? (target === 'pre' || target === 'p' ? enclosing : [...enclosing, replacement]) : run.frames;
        let common = 0;
        while (common < stack.length && common < frames.length && stack[common].frame.source === frames[common].source) common++;
        stack.splice(common);
        let parent: Node = stack.at(-1)?.element ?? fragment;
        for (const frame of frames.slice(common)) {
            let element = frame.tag === 'li' ? rendered.get(frame.source) : undefined;
            if (!element || element.parentNode !== parent) {
                element = frame.source.cloneNode(false) as HTMLElement;
                if (frame.tag === 'ol' && frame.ordinal && frame.ordinal !== 1) element.setAttribute('start', String(frame.ordinal));
                parent.appendChild(element);
            }
            stack.push({ frame, element });
            parent = element;
        }
        if (target === 'pre') {
            const content = cloneContent(run.nodes, true, true);
            if (run.selected && precedingCode && precedingCode.parentNode?.parentNode === parent) {
                precedingCode.append(root.ownerDocument.createElement('br'), root.ownerDocument.createElement('br'), content);
                continue;
            }
            const block = root.ownerDocument.createElement('pre');
            const code = root.ownerDocument.createElement('code');
            if (!run.selected) code.className = run.source.querySelector('code')?.className ?? '';
            code.append(content);
            block.append(code);
            parent.appendChild(block);
            precedingCode = run.selected ? code : undefined;
            rendered.set(run.source, block);
        } else {
            precedingCode = undefined;
            const tag = frames.at(-1)?.tag;
            if (!run.selected && tag === 'li') {
                parent.appendChild(cloneContent(run.nodes, run.code, false));
                continue;
            }
            const block = root.ownerDocument.createElement(tag === 'ul' || tag === 'ol' ? 'li' : 'p');
            block.append(cloneContent(run.nodes, run.code, run.selected));
            parent.appendChild(block);
            rendered.set(run.source, block);
        }
    }
    root.replaceChildren(fragment);
    return restore();
}
