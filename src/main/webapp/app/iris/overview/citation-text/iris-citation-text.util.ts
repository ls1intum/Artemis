import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { CITATION_REGEX, CitationRenderOptions, IrisCitationParsed } from './iris-citation-text.model';

/**
 * Token used while splitting text into citation and non-citation chunks.
 */
type CitationToken = { type: 'text'; value: string } | { type: 'citation'; value: string };

/**
 * Builds a map of citation metadata indexed by entity ID.
 * @param citationInfo Array of citation metadata.
 * @returns Map of entity ID to citation metadata.
 */
function buildCitationMap(citationInfo: IrisCitationMetaDTO[]): Map<number, IrisCitationMetaDTO> {
    const citationMap = new Map<number, IrisCitationMetaDTO>();
    citationInfo.forEach((citation) => {
        citationMap.set(citation.entityId, citation);
    });
    return citationMap;
}

/**
 * Tokenizes text into citation and non-citation chunks.
 * @param text The source text to tokenize.
 * @returns Array of tokens (text or citation).
 */
function tokenizeText(text: string): CitationToken[] {
    const tokens: CitationToken[] = [];
    let lastIndex = 0;
    for (const match of text.matchAll(CITATION_REGEX)) {
        const index = match.index ?? 0;
        if (index > lastIndex) {
            tokens.push({ type: 'text', value: text.slice(lastIndex, index) });
        }
        tokens.push({ type: 'citation', value: match[0] });
        lastIndex = index + match[0].length;
    }
    if (lastIndex < text.length) {
        tokens.push({ type: 'text', value: text.slice(lastIndex) });
    }
    return tokens;
}

/**
 * Renders tokens into HTML, grouping adjacent citations.
 * @param tokens Array of citation and text tokens.
 * @param citationMap Map of citation metadata.
 * @param options Render callbacks for single citations and groups.
 * @returns Array of rendered HTML strings.
 */
function renderTokens(tokens: CitationToken[], citationMap: Map<number, IrisCitationMetaDTO>, options: CitationRenderOptions): string[] {
    const rendered: string[] = [];
    let i = 0;
    while (i < tokens.length) {
        const token = tokens[i];
        if (token.type === 'text') {
            rendered.push(token.value);
            i += 1;
            continue;
        }

        const citationGroup: string[] = [token.value];
        let j = i + 1;
        let pendingWhitespace = '';
        while (j < tokens.length) {
            const nextToken = tokens[j];
            if (nextToken.type === 'text' && /^\s*$/.test(nextToken.value)) {
                pendingWhitespace += nextToken.value;
                j += 1;
                continue;
            }
            if (nextToken.type === 'citation') {
                citationGroup.push(nextToken.value);
                pendingWhitespace = '';
                j += 1;
                continue;
            }
            break;
        }

        if (citationGroup.length > 1) {
            const parsedGroup: IrisCitationParsed[] = [];
            citationGroup.forEach((raw) => {
                const parsed = parseCitation(raw);
                if (parsed) {
                    parsedGroup.push(parsed);
                }
            });

            if (parsedGroup.length !== citationGroup.length) {
                rendered.push(citationGroup.join(''));
            } else {
                const metas: Array<IrisCitationMetaDTO | undefined> = [];
                parsedGroup.forEach((entry) => {
                    const entityIdNum = Number(entry.entityId);
                    metas.push(entry.type === 'L' && Number.isFinite(entityIdNum) ? citationMap.get(entityIdNum) : undefined);
                });
                rendered.push(options.renderGroup(parsedGroup, metas));
            }

            if (pendingWhitespace) {
                rendered.push(pendingWhitespace);
            }
            i = j;
            continue;
        }

        const parsed = parseCitation(token.value);
        if (!parsed) {
            rendered.push(token.value);
        } else {
            const entityIdNum = Number(parsed.entityId);
            const meta = parsed.type === 'L' && Number.isFinite(entityIdNum) ? citationMap.get(entityIdNum) : undefined;
            rendered.push(options.renderSingle(parsed, meta));
        }
        if (pendingWhitespace) {
            rendered.push(pendingWhitespace);
        }
        i = j;
    }

    return rendered;
}

/**
 * Replaces citation blocks with rendered HTML, grouping adjacent citations.
 * @param text The source text that may contain citations.
 * @param citationInfo Metadata for enriching citation rendering.
 * @param options Render callbacks for single citations and groups.
 * @returns The text with citations replaced by HTML.
 */
export function replaceCitationBlocks(text: string, citationInfo: IrisCitationMetaDTO[], options: CitationRenderOptions): string {
    if (!text || !text.includes('[cite:')) {
        return text;
    }

    const citationMap = buildCitationMap(citationInfo);
    const tokens = tokenizeText(text);
    const rendered = renderTokens(tokens, citationMap, options);

    return rendered.join('');
}

/**
 * Parses a raw "[cite:...]" block into a structured citation object.
 * @param raw The raw citation string.
 * @returns The parsed citation or undefined if invalid.
 */
export function parseCitation(raw: string): IrisCitationParsed | undefined {
    const content = raw.slice(6, -1); // strip "[cite:" and trailing "]"
    const parts = content.split(':');
    if (parts.length < 7) {
        return undefined;
    }

    const type = parts[0];
    if (!isCitationType(type)) {
        return undefined;
    }

    const entityId = parts[1];
    if (!entityId) {
        return undefined;
    }

    const page = parts[2] ?? '';
    const start = parts[3] ?? '';
    const end = parts[4] ?? '';
    const keyword = parts[5] ?? '';
    const summary = parts.length > 6 ? parts.slice(6).join(':') : '';

    return { type, entityId, page, start, end, keyword, summary };
}

/**
 * Checks whether a value is a supported citation type.
 * @param value The raw type token to validate.
 * @returns True when the value is a supported citation type.
 */
export function isCitationType(value?: string): value is 'L' | 'F' {
    return value === 'L' || value === 'F';
}

/**
 * Resolves the CSS class that describes the citation type.
 * @param parsed The parsed citation entry.
 * @returns The CSS class name to apply.
 */
export function resolveCitationTypeClass(parsed: IrisCitationParsed): string {
    if (parsed.type === 'F') {
        return 'iris-citation--faq';
    }
    if (parsed.start || parsed.end) {
        return 'iris-citation--video';
    }
    if (parsed.page) {
        return 'iris-citation--slide';
    }
    return 'iris-citation--source';
}

/**
 * Gets the citation label text using the keyword or a type-based fallback.
 * @param parsed The parsed citation entry.
 * @returns The unescaped label text.
 */
export function getCitationLabelText(parsed: IrisCitationParsed): string {
    const keyword = parsed.keyword?.trim();
    const fallback = parsed.type === 'F' ? 'FAQ' : 'Source';
    return keyword || fallback;
}

/**
 * Formats a citation label using the keyword or a type-based fallback.
 * @param parsed The parsed citation entry.
 * @returns The escaped label for display.
 */
export function formatCitationLabel(parsed: IrisCitationParsed): string {
    return escapeHtml(getCitationLabelText(parsed));
}

/**
 * Escapes HTML special characters in a string.
 * @param text The raw text to escape.
 * @returns The escaped text.
 */
export function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/**
 * Removes citation blocks from text.
 * Format: [cite:LF:entityID:page:start:end:keyword:summary]
 * @param text The text to process.
 * @returns The text with citations removed.
 */
export function removeCitationBlocks(text: string): string {
    if (!text) return text;
    return text.replace(CITATION_REGEX, '').trim();
}
