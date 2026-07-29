import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

/**
 * Matches citation blocks in the form "[cite:TYPE:ENTITY_ID:PAGE:START:END:KEYWORD:SUMMARY]", optionally followed by two version
 * fields: "[cite:TYPE:ENTITY_ID:PAGE:START:END:KEYWORD:SUMMARY:ATTACHMENT_VERSION:VIDEO_VERSION]".
 * The version fields need no expression of their own: the trailing summary group already accepts colons and therefore covers them.
 * Keep in sync with Artemis server regex in src/main/java/de/tum/cit/aet/artemis/iris/service/IrisCitationService.java
 * and the regex defined in Pyris.
 */
export const CITATION_REGEX = /\[cite:[LF]:[^:[\]]+:[^:[\]]*:[^:[\]]*:[^:[\]]*:[^:[\]]*:[^[\]]*\]/g;

/**
 * A version field is a plain number, or empty when the citation is not about that kind of material.
 */
export const CITATION_VERSION_FIELD_REGEX = /^\d*$/;

/**
 * Versions of the material a citation was generated from. Both parts are optional: a slide citation only pins the attachment
 * version, a video citation only pins the transcription version.
 */
export type IrisCitationVersions = {
    attachmentVersion?: string;
    videoVersion?: string;
};

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
    versions?: IrisCitationVersions;
};

/**
 * Render hooks for converting parsed citations to HTML.
 */
export type CitationRenderOptions = {
    renderSingle: (parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO) => string;
    renderGroup: (parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>) => string;
};
