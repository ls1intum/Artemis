import { parse, postprocess, preprocess } from 'micromark';
import { DefaultTreeAdapterTypes, parseFragment } from 'parse5';

interface Destination {
    start: number;
    end: number;
    url: string;
}

/**
 * Finds link destinations without reserializing Markdown or HTML. Source offsets let the importer change only URLs,
 * retaining code examples, comments, link labels/titles, HTML quoting and original whitespace (including CRLF).
 */
export function markdownFileDestinations(source: string): Destination[] {
    const destinations: Destination[] = [];
    const htmlRanges: { start: number; end: number }[] = [];
    const events = postprocess(
        parse()
            .document()
            .write(preprocess()(source, undefined, true)),
    );
    for (const [event, token] of events) {
        if (event !== 'enter') {
            continue;
        }
        const start = token.start.offset;
        const end = token.end.offset;
        if (['resourceDestinationString', 'definitionDestinationString', 'autolinkProtocol'].includes(token.type)) {
            destinations.push({ start, end, url: source.slice(start, end) });
        } else if (token.type === 'htmlFlow' || token.type === 'htmlText' || token.type === 'data') {
            htmlRanges.push({ start, end });
        }
    }

    // Keep HTML and prose, including native HTML with unquoted paths that CommonMark does not recognize as HTML.
    // Masking other characters preserves offsets and stops HTML-looking code examples and escaped tags from becoming elements.
    // Parse the fragments together so inline <code> tags keep their scope.
    let html = '';
    let offset = 0;
    for (const range of htmlRanges) {
        html += source.slice(offset, range.start).replace(/[^\r\n]/g, ' ') + source.slice(range.start, range.end);
        offset = range.end;
    }
    html += source.slice(offset).replace(/[^\r\n]/g, ' ');
    const excludedRanges: { start: number; end: number }[] = [];
    const visit = (node: DefaultTreeAdapterTypes.Node) => {
        if ('tagName' in node) {
            const location = node.sourceCodeLocation;
            if (['pre', 'code', 'script', 'style', 'textarea'].includes(node.tagName)) {
                if (location) {
                    excludedRanges.push({ start: location.startOffset, end: location.endOffset });
                }
                return;
            }
            const attributeName = node.tagName === 'img' ? 'src' : node.tagName === 'a' ? 'href' : undefined;
            const attribute = node.attrs.find((attr) => attr.name === attributeName);
            const attributeLocation = attributeName ? location?.attrs?.[attributeName] : undefined;
            if (attribute && attributeLocation) {
                const raw = source.slice(attributeLocation.startOffset, attributeLocation.endOffset);
                const valueStart = raw.indexOf('=') + 1;
                const whitespace = raw.slice(valueStart).length - raw.slice(valueStart).trimStart().length;
                const quote = raw[valueStart + whitespace];
                const quoted = quote === '"' || quote === "'";
                const start = attributeLocation.startOffset + valueStart + whitespace + (quoted ? 1 : 0);
                const end = attributeLocation.endOffset - (quoted ? 1 : 0);
                destinations.push({ start, end, url: attribute.value });
            }
        }
        if ('childNodes' in node) {
            node.childNodes.forEach(visit);
        }
    };
    visit(parseFragment(html, { sourceCodeLocationInfo: true }));
    return destinations.filter((destination) => !excludedRanges.some((range) => destination.start >= range.start && destination.start < range.end));
}
