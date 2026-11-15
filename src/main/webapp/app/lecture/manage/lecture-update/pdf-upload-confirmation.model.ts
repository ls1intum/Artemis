/** The router state key under which the lecture list hands a {@link PdfUploadConfirmation} to the lecture editor. */
export const PDF_UPLOAD_CONFIRMATION_STATE_KEY = 'pdfUploadConfirmation';

/**
 * What the lecture editor confirms after PDFs were dropped on the lecture list: whether a lecture was created for them or they were added to an
 * existing one, which files they were, and from when students can see them. It travels as router state, so it only holds serializable values.
 */
export interface PdfUploadConfirmation {
    lectureCreated: boolean;
    fileNames: string[];
    /** ISO 8601 date-time of the earliest release of the new content items, if they have one. */
    releaseDate?: string;
}
