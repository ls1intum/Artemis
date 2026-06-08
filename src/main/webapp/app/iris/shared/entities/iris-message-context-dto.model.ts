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
