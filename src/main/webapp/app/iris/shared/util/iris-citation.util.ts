import { IrisCitationDTO } from 'app/iris/shared/entities/iris-citation-dto.model';

const citationRegex = /\[cite:\s*(\d+)]/gi;

export function formatMarkdownWithCitations(markdownText: string | undefined, citations: IrisCitationDTO[] | undefined): string {
    if (!markdownText) {
        return '';
    }
    if (!citations || citations.length === 0) {
        return markdownText;
    }

    const citationsByIndex = new Map(citations.map((citation) => [citation.index, citation]));
    return markdownText.replace(citationRegex, (match, indexValue) => {
        const citation = citationsByIndex.get(Number(indexValue));
        if (!citation) {
            return match;
        }

        const type = (citation.type ?? 'source').toLowerCase();
        const typeClass = ['slide', 'video', 'faq'].includes(type) ? type : 'source';
        const keyword = resolveCitationKeyword(citation);
        const label = keyword ? `${type} ${keyword}` : type;
        const safeLabel = escapeHtml(label);
        return `<span class="iris-citation iris-citation--${typeClass}"><span class="iris-citation__icon" aria-hidden="true"></span><span class="iris-citation__text">${safeLabel}</span></span>`;
    });
}

function resolveCitationKeyword(citation: IrisCitationDTO): string | undefined {
    return citation.keyword || citation.faqQuestionTitle || citation.unitName || citation.lectureName || citation.summary;
}

function escapeHtml(value: string): string {
    return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}
