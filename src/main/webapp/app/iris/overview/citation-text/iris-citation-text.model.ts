import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

/**
 * Matches citation blocks in the form "[cite:TYPE:ENTITY_ID:PAGE:START:END:KEYWORD:SUMMARY]".
 * Enforces exactly 7 colons (complete format).
 * Keep in sync with Artemis server regex in src/main/java/de/tum/cit/aet/artemis/iris/service/IrisCitationService.java
 * and the regex defined in Pyris.
 */
export const CITATION_REGEX = /\[cite:[LF]:[^:[\]]+:[^:[\]]*:[^:[\]]*:[^:[\]]*:[^:[\]]*:[^[\]]*\]/g;

/**
 * Parsed representation of a single citation block of the form "[cite:type:entityId:page:start:end:keyword:summary]".
 */
export type IrisCitationParsed = {
    type: 'L' | 'F'; // L = lecture material and F = FAQ as underlying source
    entityId: string;
    page: string;
    start: string;
    end: string;
    keyword: string;
    summary: string;
};

/**
 * Render hooks for converting parsed citations to HTML.
 */
export type CitationRenderOptions = {
    renderSingle: (parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO) => string;
    renderGroup: (parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>) => string;
};
