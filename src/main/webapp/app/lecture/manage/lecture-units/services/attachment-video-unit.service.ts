import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnitApi } from 'app/openapi/api/attachment-video-unit-api';
import { AttachmentVideoUnitPlaylistApi } from 'app/openapi/api/attachment-video-unit-playlist-api';
import type { AttachmentVideoUnit as GeneratedAttachmentVideoUnit } from 'app/openapi/model/attachment-video-unit';
import type { HiddenPageInfo } from 'app/openapi/model/hidden-page-info';
import type { SlideOrder } from 'app/openapi/model/slide-order';
import { toAttachmentRequest, toLectureUnit, toLectureUnitRequest } from 'app/lecture/shared/util/generated-lecture.util';

/**
 * Ngsw-worker is bypassed temporarily to fix Chromium file upload issue
 * See: https://issues.chromium.org/issues/374550348
 */
const NGSW_BYPASS = { 'ngsw-bypass': 'true' };

/** What an update of an attachment video unit sends besides the unit itself. */
export interface AttachmentVideoUnitUpdate {
    attachment?: Attachment;
    file?: File;
    studentVersion?: File;
    hiddenPages?: HiddenPageInfo[];
    pageOrder?: SlideOrder[];
    /** Sent when students are to be notified about a new file; the server notifies whenever it is present, also when it is empty. */
    notificationText?: string;
}

/** Names a file for upload; the file itself is kept when it already has the wanted name or none is wanted. */
export function withFileName(file: File, fileName?: string): File {
    return fileName === undefined || file.name === fileName ? file : new File([file], fileName, { type: file.type });
}

@Service()
export class AttachmentVideoUnitService {
    private httpClient = inject(HttpClient);
    private attachmentVideoUnitApi = inject(AttachmentVideoUnitApi);
    private attachmentVideoUnitPlaylistApi = inject(AttachmentVideoUnitPlaylistApi);

    findById(attachmentVideoUnitId: number, lectureId: number): Observable<AttachmentVideoUnit> {
        return this.attachmentVideoUnitApi.getAttachmentVideoUnit(attachmentVideoUnitId, lectureId).pipe(map((unit) => toLectureUnit<AttachmentVideoUnit>(unit)));
    }

    create(lectureId: number, unit: AttachmentVideoUnit, attachment?: Attachment, file?: File): Observable<AttachmentVideoUnit> {
        return this.attachmentVideoUnitApi
            .createAttachmentVideoUnit(lectureId, toLectureUnitRequest<GeneratedAttachmentVideoUnit>(unit), true, attachment && toAttachmentRequest(attachment), file, {
                headers: NGSW_BYPASS,
            })
            .pipe(map((created) => toLectureUnit<AttachmentVideoUnit>(created)));
    }

    /**
     * Updates an attachment video unit.
     * @param lectureId the id of the lecture
     * @param attachmentVideoUnitId the id of the unit
     * @param unit the unit
     * @param update a new file with its attachment if one is uploaded, and what else the update changes
     * @param context the context of the request, for example to show a failure in place instead of an alert
     */
    update(
        lectureId: number,
        attachmentVideoUnitId: number,
        unit: AttachmentVideoUnit,
        update: AttachmentVideoUnitUpdate = {},
        context?: HttpContext,
    ): Observable<AttachmentVideoUnit> {
        const { attachment, file, studentVersion, hiddenPages, pageOrder, notificationText } = update;
        return this.attachmentVideoUnitApi
            .updateAttachmentVideoUnit(
                lectureId,
                attachmentVideoUnitId,
                toLectureUnitRequest<GeneratedAttachmentVideoUnit>(unit),
                true,
                notificationText,
                attachment && toAttachmentRequest(attachment),
                file,
                studentVersion,
                hiddenPages,
                pageOrder,
                { headers: NGSW_BYPASS, context },
            )
            .pipe(map((updated) => toLectureUnit<AttachmentVideoUnit>(updated)));
    }

    /**
     * Retrieve the file associated with a given attachment ID as a Blob object
     *
     * @param courseId The ID of the course that the Attachment Unit belongs to
     * @param attachmentVideoUnitId The ID of the attachment to retrieve
     * @returns An Observable that emits the Blob object of the file when the HTTP request completes successfully
     */
    getAttachmentFile(courseId: number, attachmentVideoUnitId: number): Observable<Blob> {
        return this.httpClient.get(`api/core/files/courses/${courseId}/attachment-video-units/${attachmentVideoUnitId}`, { responseType: 'blob' });
    }

    /**
     * Resolves the TUM Live video of a unit to its HLS playlist URL (.m3u8). The URL is signed and valid for seven hours, so
     * it is requested again whenever the unit is opened.
     *
     * @param lectureUnitId - The attachment video unit
     * @returns Observable<string | undefined> - The playlist URL if found, undefined otherwise
     */
    getPlaylistUrl(lectureUnitId: number): Observable<string | undefined> {
        return this.attachmentVideoUnitPlaylistApi.getPlaylist(lectureUnitId).pipe(catchError(() => of(undefined)));
    }

    /**
     * Creates an attachment video unit from a PDF file.
     * Derives the unit name from the filename and sets release date to 15 minutes in the future.
     *
     * @param lectureId - The ID of the lecture to add the unit to
     * @param file - The PDF file to create the attachment unit from
     * @returns Observable of the created AttachmentVideoUnit
     */
    createAttachmentVideoUnitFromFile(lectureId: number, file: File): Observable<AttachmentVideoUnit> {
        const unitName = file.name
            .replace(/\.pdf$/i, '')
            .replace(/[_-]/g, ' ')
            .trim();
        const releaseDate = dayjs().add(15, 'minutes');

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.name = unitName;
        attachmentVideoUnit.releaseDate = releaseDate;

        const attachment = new Attachment();
        attachment.name = unitName;
        attachment.releaseDate = releaseDate;
        attachment.attachmentType = AttachmentType.FILE;

        return this.create(lectureId, attachmentVideoUnit, attachment, file);
    }
}
