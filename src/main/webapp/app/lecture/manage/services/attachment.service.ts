import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { convertDateFromClient, convertDateFromServer } from 'app/shared/util/date.utils';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { cloneDeep } from 'lodash-es';
import { addPublicFilePrefix } from 'app/app.constants';

type EntityResponseType = HttpResponse<Attachment>;
type EntityArrayResponseType = HttpResponse<Attachment[]>;

@Injectable({ providedIn: 'root' })
export class AttachmentService {
    protected http = inject(HttpClient);

    public resourceUrl = 'api/lecture/attachments';

    /**
     * Update an existing attachment
     * @param attachmentId the id of the attachment to update
     * @param attachment the attachment object holding the updated values
     * @param file the file to save as an attachment if it was changed (optional)
     * @param req optional request parameters
     */
    update(attachmentId: number, attachment: Attachment, file?: File, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.convertAttachmentDatesFromClient(attachment);

        /** Ngsw-worker is bypassed temporarily to fix Chromium file upload issue
         * See: https://issues.chromium.org/issues/374550348
         **/
        return this.http
            .put<Attachment>(this.resourceUrl + '/' + attachmentId, this.createFormData(copy, file), { headers: { 'ngsw-bypass': 'true' }, params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertAttachmentResponseDatesFromServer(res)));
    }

    /**
     * Return the attachment with the given id
     *
     * @param attachmentId the id of the attachment to find
     */
    find(attachmentId: number): Observable<EntityResponseType> {
        return this.http
            .get<Attachment>(`${this.resourceUrl}/${attachmentId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertAttachmentResponseDatesFromServer(res)));
    }

    /**
     * Return all attachments for the given lecture
     * @param lectureId the id of the lecture to find attachments for
     */
    findAllByLectureId(lectureId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Attachment[]>(`api/lecture/lectures/${lectureId}/attachments`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertAttachmentArrayResponseDatesFromServer(res)));
    }

    /**
     * Delete the attachment with the given id
     * @param attachmentId the id of the attachment to delete
     */
    delete(attachmentId: number): Observable<HttpResponse<void>> {
        return this.http.delete<any>(`${this.resourceUrl}/${attachmentId}`, { observe: 'response' });
    }

    convertAttachmentDatesFromClient(attachment: Attachment): Attachment {
        // Deep clone is applied to preserve all nested properties of the attachment
        return Object.assign({}, cloneDeep(attachment), {
            releaseDate: convertDateFromClient(attachment.releaseDate),
            uploadDate: convertDateFromClient(attachment.uploadDate),
        });
    }

    convertAttachmentFromServer(attachment?: Attachment) {
        if (attachment) {
            attachment.releaseDate = convertDateFromServer(attachment.releaseDate);
            attachment.uploadDate = convertDateFromServer(attachment.uploadDate);
            attachment.linkUrl = addPublicFilePrefix(attachment.link);
        }
        return attachment;
    }

    private convertAttachmentResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.convertAttachmentFromServer(res.body);
        }
        return res;
    }

    private convertAttachmentArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((attachment: Attachment) => {
                this.convertAttachmentFromServer(attachment);
            });
        }
        return res;
    }

    private createFormData(attachment: Attachment, file?: File) {
        const formData = new FormData();
        formData.append('attachment', objectToJsonBlob(attachment));
        if (file) {
            formData.append('file', file);
        }
        return formData;
    }

    /**
     * Retrieve the file associated with a given attachment ID as a Blob object
     *
     * @param courseId The ID of the course that the attachment belongs to
     * @param attachmentId The ID of the attachment to retrieve
     * @returns An Observable that emits the Blob object of the file when the HTTP request completes successfully
     */
    getAttachmentFile(courseId: number, attachmentId: number): Observable<Blob> {
        return this.http.get(`api/core/files/courses/${courseId}/attachments/${attachmentId}`, { responseType: 'blob' });
    }
}
