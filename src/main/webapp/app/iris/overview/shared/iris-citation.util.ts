import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

export const CITATION_REGEX = /\[cite:[^\]]+\]/g;

export type IrisCitationParsed = {
    type: 'L' | 'F';
    entityId: number;
    page: string;
    start: string;
    end: string;
    keyword: string;
    summary?: string;
};

type CitationToken = { type: 'text'; value: string } | { type: 'citation'; value: string };

export type CitationRenderOptions = {
    renderSingle: (parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO) => string;
    renderGroup: (parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>) => string;
    preserveGroupOnSingleParsed?: boolean;
};

export function replaceCitationBlocks(text: string, citationInfo: IrisCitationMetaDTO[], options: CitationRenderOptions): string {
    if (!text || !text.includes('[cite:')) {
        return text;
    }

    const citationMap = new Map<number, IrisCitationMetaDTO>();
    citationInfo.forEach((citation) => citationMap.set(citation.entityId, citation));

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

            if (parsedGroup.length === 0) {
                rendered.push(citationGroup.join(''));
            } else if (parsedGroup.length === 1 && !options.preserveGroupOnSingleParsed) {
                const meta = parsedGroup[0].type === 'L' ? citationMap.get(parsedGroup[0].entityId) : undefined;
                rendered.push(options.renderSingle(parsedGroup[0], meta));
            } else {
                const metas: Array<IrisCitationMetaDTO | undefined> = [];
                parsedGroup.forEach((entry) => {
                    metas.push(entry.type === 'L' ? citationMap.get(entry.entityId) : undefined);
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
            const meta = parsed.type === 'L' ? citationMap.get(parsed.entityId) : undefined;
            rendered.push(options.renderSingle(parsed, meta));
        }
        if (pendingWhitespace) {
            rendered.push(pendingWhitespace);
        }
        i = j;
    }

    return rendered.join('');
}

export function parseCitation(raw: string): IrisCitationParsed | undefined {
    const content = raw.slice(6, -1); // strip "[cite:" and trailing "]"
    const parts = content.split(':');
    if (parts.length < 2) {
        return undefined;
    }

    const type = parts[0];
    if (!isCitationType(type)) {
        return undefined;
    }

    const entityId = Number(parts[1]);
    if (!Number.isFinite(entityId)) {
        return undefined;
    }

    const page = parts[2] ?? '';
    const start = parts[3] ?? '';
    const end = parts[4] ?? '';
    const keyword = parts[5] ?? '';
    const summary = parts.length > 6 ? parts.slice(6).join(':') : '';

    return { type, entityId, page, start, end, keyword, summary };
}

export function isCitationType(value?: string): value is 'L' | 'F' {
    return value === 'L' || value === 'F';
}

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

export function formatCitationLabel(parsed: IrisCitationParsed): string {
    const keyword = parsed.keyword?.trim();
    const fallback = parsed.type === 'F' ? 'FAQ' : 'Source';
    const label = keyword || fallback;
    return escapeHtml(label);
}

export function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
