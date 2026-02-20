/**
 * A transcript segment corresponding to a portion of the video.
 */
export interface TranscriptSegment {
    startTime: number;
    endTime: number;
    text: string;
    slideNumber?: number;
}
