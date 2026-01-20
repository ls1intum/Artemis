import { IrisCitationDTO } from 'app/iris/shared/entities/iris-citation-dto.model';

const citationRegex = /\[cite:\s*(\d+)]/gi;
const multiCitationRegex = /(\[cite:\s*\d+](?:\s*\[cite:\s*\d+])+)/gi;

export function formatMarkdownWithCitations(markdownText: string | undefined, citations: IrisCitationDTO[] | undefined): string {
    if (!markdownText) {
        return '';
    }
    if (!citations || citations.length === 0) {
        return markdownText;
    }

    const citationsByIndex = new Map(citations.map((citation) => [citation.index, citation]));
    const formattedGroups = markdownText.replace(multiCitationRegex, (match) => {
        const indices = Array.from(match.matchAll(citationRegex), (groupMatch) => Number(groupMatch[1]));
        const groupCitations = indices.map((index) => citationsByIndex.get(index)).filter((citation): citation is IrisCitationDTO => citation !== undefined);
        if (groupCitations.length !== indices.length) {
            return match;
        }
        return formatCitationGroup(groupCitations);
    });

    return formattedGroups.replace(citationRegex, (match, indexValue) => {
        const citation = citationsByIndex.get(Number(indexValue));
        if (!citation) {
            return match;
        }

        return formatSingleCitation(citation);
    });
}

function resolveCitationKeyword(citation: IrisCitationDTO): string | undefined {
    return citation.keyword || citation.faqQuestionTitle || citation.unitName || citation.lectureName || citation.summary;
}

function escapeHtml(value: string): string {
    return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

function formatSingleCitation(citation: IrisCitationDTO): string {
    const type = (citation.type ?? 'source').toLowerCase();
    const typeClass = ['slide', 'video', 'faq'].includes(type) ? type : 'source';
    const keyword = resolveCitationKeyword(citation);
    const label = keyword ?? type;
    const safeLabel = escapeHtml(label);
    const summaryMarkup = formatCitationSummaryMarkup(citation);
    const summaryClass = summaryMarkup ? ' iris-citation--has-summary' : '';
    return `<span class="iris-citation iris-citation--${typeClass}${summaryClass}"><span class="iris-citation__icon" aria-hidden="true"></span><span class="iris-citation__text">${safeLabel}</span>${summaryMarkup}</span>`;
}

function formatCitationGroup(citations: IrisCitationDTO[]): string {
    const primaryCitation = citations[0];
    const type = (primaryCitation.type ?? 'source').toLowerCase();
    const typeClass = ['slide', 'video', 'faq'].includes(type) ? type : 'source';
    const keyword = resolveCitationKeyword(primaryCitation);
    const label = keyword ?? type;
    const safeLabel = escapeHtml(label);
    const summaryMarkup = formatCitationGroupSummary(citations);
    const summaryClass = summaryMarkup ? ' iris-citation-group--has-summary' : '';
    const extraCount = citations.length - 1;
    const countMarkup = extraCount > 0 ? `<span class="iris-citation__count" aria-hidden="true">+${extraCount}</span>` : '';
    return `<span class="iris-citation-group${summaryClass}"><span class="iris-citation iris-citation--${typeClass}"><span class="iris-citation__icon" aria-hidden="true"></span><span class="iris-citation__text">${safeLabel}</span></span>${countMarkup}${summaryMarkup}</span>`;
}

function formatCitationSummaryMarkup(citation: IrisCitationDTO): string {
    const details = formatCitationDetails(citation);
    if (!details) {
        return '';
    }
    return `<span class="iris-citation__summary">${details}</span>`;
}

function formatCitationGroupSummary(citations: IrisCitationDTO[]): string {
    const detailsByCitation = citations.map((citation) => formatCitationDetails(citation));
    const summaryItems = detailsByCitation.map((details, index) => {
        const activeClass = index === 0 ? ' is-active' : '';
        return `<span class="iris-citation__summary-item${activeClass}" data-summary-index="${index}">${details}</span>`;
    });
    const hasSummary = detailsByCitation.some((details) => details !== '');
    if (!hasSummary) {
        return '';
    }
    const nav = `<span class="iris-citation__nav"><span class="iris-citation__nav-button iris-citation__nav-button--prev" aria-label="Previous citation">&lt;</span><span class="iris-citation__nav-count">1/${citations.length}</span><span class="iris-citation__nav-button iris-citation__nav-button--next" aria-label="Next citation">&gt;</span></span>`;
    return `<span class="iris-citation__summary"><span class="iris-citation__summary-content">${summaryItems.join('')}</span>${nav}</span>`;
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

    return details.length === 0 ? '' : details.join('<br>');
}
