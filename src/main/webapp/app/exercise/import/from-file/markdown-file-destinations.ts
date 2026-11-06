import { parse, postprocess, preprocess } from 'micromark';
import { DefaultTreeAdapterTypes, parseFragment } from 'parse5';

interface Destination {
    start: number;
    end: number;
    url: string;
}

/** Returns the query and fragment for a destination that points to the archived file. */
export function markdownFileDestinationSuffix(url: string, fileName: string): string | undefined {
    try {
        const parsedUrl = new URL(url, 'http://artemis.invalid');
        const decodedPath = decodeURIComponent(parsedUrl.pathname);
        if (!decodedPath.endsWith(`markdown/${fileName}`)) {
            return undefined;
        }
        return `${parsedUrl.search}${parsedUrl.hash}`;
    } catch {
        return undefined;
    }
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
            const destination = htmlDestination(source, node);
            if (destination) {
                destinations.push(destination);
            }
        }
        if ('childNodes' in node) {
            node.childNodes.forEach(visit);
        }
    };
    visit(parseFragment(html, { sourceCodeLocationInfo: true }));
    return destinations.filter((destination) => !excludedRanges.some((range) => destination.start >= range.start && destination.start < range.end));
}

/** Reads a destination attribute's value and exact source range, leaving its name, spacing and quotes intact. */
function htmlDestination(source: string, node: DefaultTreeAdapterTypes.Element): Destination | undefined {
    let attributeName: string;
    if (node.tagName === 'img') {
        attributeName = 'src';
    } else if (node.tagName === 'a') {
        attributeName = 'href';
    } else {
        return undefined;
    }
    const attribute = node.attrs.find((attr) => attr.name === attributeName);
    const location = node.sourceCodeLocation?.attrs?.[attributeName];
    if (!attribute || !location) {
        return undefined;
    }
    const raw = source.slice(location.startOffset, location.endOffset);
    const valueStart = raw.indexOf('=') + 1;
    const whitespace = raw.slice(valueStart).length - raw.slice(valueStart).trimStart().length;
    const quote = raw[valueStart + whitespace];
    const quoted = quote === '"' || quote === "'";
    return {
        start: location.startOffset + valueStart + whitespace + (quoted ? 1 : 0),
        end: location.endOffset - (quoted ? 1 : 0),
        url: attribute.value,
    };
}
