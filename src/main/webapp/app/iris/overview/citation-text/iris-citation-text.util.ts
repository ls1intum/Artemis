import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { CITATION_REGEX, CitationRenderOptions, IrisCitationParsed } from './iris-citation-text.model';

/**
 * Citation parsing constants.
 */
const CITE_PREFIX = '[cite:';
const CITE_AMOUNT_PARTS = 7;
const INDEX_TYPE_IN_CITE_PARTS = 0;
const INDEX_ENTITY_ID_IN_CITE_PARTS = 1;
const INDEX_PAGE_IN_CITE_PARTS = 2;
const INDEX_START_IN_CITE_PARTS = 3;
const INDEX_END_IN_CITE_PARTS = 4;
const INDEX_KEYWORD_IN_CITE_PARTS = 5;
const INDEX_SUMMARY_IN_CITE_PARTS = 6;

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
    return new Map(citationInfo.map((citation) => [citation.entityId, citation]));
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
 * Result of collecting adjacent citations.
 */
type CitationCollectionResult = {
    citationGroup: string[];
    nextTokenIndex: number;
    pendingWhitespace: string;
};

/**
 * Collects adjacent citation tokens, grouping those separated only by whitespace.
 * @param tokens Array of all tokens.
 * @param startIndex Index of the first citation token.
 * @returns Collection result with citation group and next index to process.
 */
function collectAdjacentCitations(tokens: CitationToken[], startIndex: number): CitationCollectionResult {
    const citationGroup: string[] = [tokens[startIndex].value];
    let nextTokenIndex = startIndex + 1;
    let pendingWhitespace = '';

    while (nextTokenIndex < tokens.length) {
        const nextToken = tokens[nextTokenIndex];
        if (nextToken.type === 'text' && /^\s*$/.test(nextToken.value)) {
            pendingWhitespace += nextToken.value;
            nextTokenIndex += 1;
            continue;
        }
        if (nextToken.type === 'citation') {
            citationGroup.push(nextToken.value);
            pendingWhitespace = '';
            nextTokenIndex += 1;
            continue;
        }
        break;
    }

    return { citationGroup, nextTokenIndex, pendingWhitespace };
}

/**
 * Renders a group of citations.
 * @param citationGroup Array of raw citation strings.
 * @param citationMap Map of citation metadata.
 * @param options Render callbacks.
 * @returns Rendered HTML string.
 */
function renderCitationGroup(citationGroup: string[], citationMap: Map<number, IrisCitationMetaDTO>, options: CitationRenderOptions): string {
    const parsedGroup = citationGroup.map((raw) => parseCitation(raw)).filter((parsed): parsed is IrisCitationParsed => parsed !== undefined);

    if (parsedGroup.length !== citationGroup.length) {
        return citationGroup.join('');
    }

    const citationMetadata = parsedGroup.map((entry) => {
        const entityIdNum = Number(entry.entityId);
        return entry.type === 'L' && Number.isFinite(entityIdNum) ? citationMap.get(entityIdNum) : undefined;
    });

    return options.renderGroup(parsedGroup, citationMetadata);
}

/**
 * Renders a single citation token.
 * @param citationValue Raw citation string.
 * @param citationMap Map of citation metadata.
 * @param options Render callbacks.
 * @returns Rendered HTML string.
 */
function renderSingleCitation(citationValue: string, citationMap: Map<number, IrisCitationMetaDTO>, options: CitationRenderOptions): string {
    const parsed = parseCitation(citationValue);
    if (!parsed) {
        return citationValue;
    }

    const entityIdNum = Number(parsed.entityId);
    const meta = parsed.type === 'L' && Number.isFinite(entityIdNum) ? citationMap.get(entityIdNum) : undefined;
    return options.renderSingle(parsed, meta);
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
    let tokenIndex = 0;

    while (tokenIndex < tokens.length) {
        const token = tokens[tokenIndex];

        if (token.type === 'text') {
            rendered.push(token.value);
            tokenIndex += 1;
            continue;
        }

        const { citationGroup, nextTokenIndex, pendingWhitespace } = collectAdjacentCitations(tokens, tokenIndex);

        if (citationGroup.length > 1) {
            rendered.push(renderCitationGroup(citationGroup, citationMap, options));
        } else {
            rendered.push(renderSingleCitation(token.value, citationMap, options));
        }

        if (pendingWhitespace) {
            rendered.push(pendingWhitespace);
        }

        tokenIndex = nextTokenIndex;
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
    const content = raw.slice(CITE_PREFIX.length, -1); // strip "[cite:" and trailing "]"
    const parts = content.split(':');
    if (parts.length < CITE_AMOUNT_PARTS) {
        return undefined;
    }

    const type = parts[INDEX_TYPE_IN_CITE_PARTS];
    if (!isCitationType(type)) {
        return undefined;
    }

    const entityId = parts[INDEX_ENTITY_ID_IN_CITE_PARTS];
    if (!entityId) {
        return undefined;
    }

    const page = parts[INDEX_PAGE_IN_CITE_PARTS] ?? '';
    const start = parts[INDEX_START_IN_CITE_PARTS] ?? '';
    const end = parts[INDEX_END_IN_CITE_PARTS] ?? '';
    const keyword = parts[INDEX_KEYWORD_IN_CITE_PARTS] ?? '';
    const summary = parts.length > INDEX_SUMMARY_IN_CITE_PARTS ? parts.slice(INDEX_SUMMARY_IN_CITE_PARTS).join(':') : '';

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
 * Map for HTML character escaping.
 */
const HTML_ESCAPE_MAP: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
};

/**
 * Escapes HTML special characters in a string.
 * @param text The raw text to escape.
 * @returns The escaped text.
 */
export function escapeHtml(text: string): string {
    return text.replace(/[&<>"']/g, (char) => HTML_ESCAPE_MAP[char]);
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
