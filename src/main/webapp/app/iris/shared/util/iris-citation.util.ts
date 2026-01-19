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
        const label = keyword ?? type;
        const safeLabel = escapeHtml(label);
        const summaryMarkup = formatCitationDetails(citation);
        const summaryClass = summaryMarkup ? ' iris-citation--has-summary' : '';
        return `<span class="iris-citation iris-citation--${typeClass}${summaryClass}"><span class="iris-citation__icon" aria-hidden="true"></span><span class="iris-citation__text">${safeLabel}</span>${summaryMarkup}</span>`;
    });
}

function resolveCitationKeyword(citation: IrisCitationDTO): string | undefined {
    return citation.keyword || citation.faqQuestionTitle || citation.unitName || citation.lectureName || citation.summary;
}

function escapeHtml(value: string): string {
    return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

function formatCitationDetails(citation: IrisCitationDTO): string {
    const details: string[] = [];
    const addDetail = (label: string, value: string | number | undefined) => {
        if (value === undefined || value === '') {
            return;
        }
        details.push(`${escapeHtml(label)}: ${escapeHtml(String(value))}`);
    };

    const type = citation.type;
    if (type === 'slide') {
        addDetail('keyword', citation.keyword);
        addDetail('summary', citation.summary);
        addDetail('lecture', citation.lectureName);
        addDetail('unit', citation.unitName);
        addDetail('page', citation.page);
    } else if (type === 'video') {
        addDetail('keyword', citation.keyword);
        addDetail('summary', citation.summary);
        addDetail('lecture', citation.lectureName);
        addDetail('unit', citation.unitName);
        addDetail('start time', citation.startTime);
        addDetail('end time', citation.endTime);
    } else if (type === 'faq') {
        addDetail('summary', citation.summary);
        addDetail('keyword', citation.keyword);
        addDetail('faq question title', citation.faqQuestionTitle);
    } else {
        addDetail('keyword', citation.keyword);
        addDetail('summary', citation.summary);
    }

    if (details.length === 0) {
        return '';
    }

    return `<span class="iris-citation__summary">${details.join('<br>')}</span>`;
}
