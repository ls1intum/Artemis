import dayjs from 'dayjs/esm';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

/**
 * Lightweight metadata for a single exercise version, used in the timeline sidebar.
 */
export interface ExerciseVersionMetadata {
    /** Server-assigned version identifier. */
    id: number;
    /** The user who created (or triggered) this version. */
    author?: UserPublicInfoDTO;
    /** Timestamp when the version was persisted. */
    createdDate?: dayjs.Dayjs;
}

/**
 * A single page of {@link ExerciseVersionMetadata} entries together with
 * pagination cursors returned by the server.
 */
export interface ExerciseVersionPage {
    /** Version entries contained in this page. */
    versions: ExerciseVersionMetadata[];
    /** Zero-based index of the next page, or `undefined` if this is the last page. */
    nextPage?: number;
    /** Total number of versions across all pages. */
    totalItems: number;
}
