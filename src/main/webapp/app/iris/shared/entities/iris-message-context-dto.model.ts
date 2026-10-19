/**
 * Base interface for context information attached to Iris messages.
 * Context provides additional information about what the user is currently viewing or working on,
 * which helps Iris give more relevant and contextual responses.
 *
 * Context information is NOT persisted in the database - it is only sent to Pyris for enhanced responses.
 */
export interface IrisMessageContextDTO {
    type: string;
}

/**
 * Provider interface for collecting context from visible lecture materials.
 * Returns a list of context objects (video and/or slides) from currently visible lecture units.
 */
export interface LectureContextsProvider {
    getVisibleContexts(): IrisMessageContextDTO[];
}

/**
 * Context information for video content in lectures.
 * Provides information about which lecture unit video the user is watching and the current timestamp.
 */
export interface IrisVideoContextDTO extends IrisMessageContextDTO {
    type: 'video';
    lectureUnitId: number;
    timestamp: number;
}

/**
 * Context information for slide/PDF content in lectures.
 * Provides information about which lecture unit slides the user is viewing and the current page number.
 */
export interface IrisSlidesContextDTO extends IrisMessageContextDTO {
    type: 'slides';
    lectureUnitId: number;
    page: number;
}

/**
 * Context information for the combined lecture view (fullscreen mode).
 * Indicates that the user is viewing a lecture unit in the combined view.
 *
 * The combined view only exists when there is at least a slide or a video, so the slide context
 * (current page) and/or video context (current timestamp) of the unit are nested directly on this
 * context instead of being sent as separate top-level context entries. Either nested context may be
 * absent (e.g. no PDF is open, or the video has not been played yet), but not both. The lecture unit
 * ID is derived from whichever nested context is present and is used to scope RAG search.
 */
export interface IrisCombinedViewContextDTO extends IrisMessageContextDTO {
    type: 'combinedView';
    slides?: IrisSlidesContextDTO;
    video?: IrisVideoContextDTO;
}
