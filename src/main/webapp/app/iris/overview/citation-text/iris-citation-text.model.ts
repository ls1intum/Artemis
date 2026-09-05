import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

/**
 * Matches citation blocks in the form "[cite:TYPE:ENTITY_ID:PAGE:START:END:KEYWORD:SUMMARY]", optionally followed by one version
 * field: "[cite:TYPE:ENTITY_ID:PAGE:START:END:KEYWORD:SUMMARY:va3]" for slides, ":vt3" for a video.
 * The version field needs no expression of its own: the trailing summary group already accepts colons and therefore covers it.
 * Keep in sync with Artemis server regex in src/main/java/de/tum/cit/aet/artemis/iris/service/IrisCitationService.java
 * and the regex defined in Pyris.
 */
export const CITATION_REGEX = /\[cite:[LF]:[^:[\]]+:[^:[\]]*:[^:[\]]*:[^:[\]]*:[^:[\]]*:[^[\]]*\]/g;

/**
 * A version field is one of the two tags followed by a number: "va" for the attachment a slide citation is about, "vt" for the
 * transcription a video citation is about. The tag is what makes the field distinguishable from a summary, which may contain
 * colons and may well end in a number - reading such a summary as a version would let a citation of changed material pass as
 * current. Keep in sync with VERSION_FIELD_PATTERN in IrisCitationService.java.
 */
export const CITATION_VERSION_FIELD_REGEX = /^v([at])(\d+)$/;

/**
 * Version of the material a citation was generated from, together with which kind of material that is. A citation is about either
 * a slide or a video, never both, so there is exactly one - the server settled which when it stamped the marker, and the client
 * reads it rather than deriving it a second time from the timestamps.
 */
export type IrisCitationVersion = {
    kind: 'attachment' | 'video';
    version: string;
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
    pinnedVersion?: IrisCitationVersion;
};

/**
 * Render hooks for converting parsed citations to HTML.
 */
export type CitationRenderOptions = {
    renderSingle: (parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO) => string;
    renderGroup: (parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>) => string;
};
