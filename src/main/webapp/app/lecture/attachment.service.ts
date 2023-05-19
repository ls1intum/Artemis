import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { Attachment } from 'app/entities/attachment.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { objectToJsonBlob } from 'app/utils/blob-util';

type EntityResponseType = HttpResponse<Attachment>;
type EntityArrayResponseType = HttpResponse<Attachment[]>;

@Injectable({ providedIn: 'root' })
export class AttachmentService {
    public resourceUrl = 'api/attachments';

    constructor(protected http: HttpClient) {}

    create(attachment: Attachment, file: File): Observable<EntityResponseType> {
        const copy = this.convertAttachmentDatesFromClient(attachment);
        // avoid potential issues when sending the attachment to the server
        if (copy.attachmentUnit) {
            copy.attachmentUnit.lecture = undefined;
            copy.attachmentUnit.learningGoals = undefined;
        }
        if (copy.lecture) {
            copy.lecture.lectureUnits = undefined;
            copy.lecture.course = undefined;
            copy.lecture.posts = undefined;
        }

        const formData = new FormData();
        formData.append('attachment', objectToJsonBlob(copy));
        formData.append('file', file);

        return this.http
            .post<Attachment>(this.resourceUrl, formData, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertAttachmentResponseDatesFromServer(res)));
    }

    update(attachmentId: number, attachment: Attachment, file?: File, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.convertAttachmentDatesFromClient(attachment);

        const formData = new FormData();
        formData.append('attachment', objectToJsonBlob(copy));
        if (file) {
            formData.append('file', file);
        }
        return this.http
            .put<Attachment>(this.resourceUrl + '/' + attachmentId, formData, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertAttachmentResponseDatesFromServer(res)));
    }

    find(attachmentId: number): Observable<EntityResponseType> {
        return this.http
            .get<Attachment>(`${this.resourceUrl}/${attachmentId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertAttachmentResponseDatesFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Attachment[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertAttachmentArrayResponseDatesFromServer(res)));
    }

    findAllByLectureId(lectureId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Attachment[]>(`api/lectures/${lectureId}/attachments`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertAttachmentArrayResponseDatesFromServer(res)));
    }

    delete(attachmentId: number): Observable<HttpResponse<void>> {
        return this.http.delete<any>(`${this.resourceUrl}/${attachmentId}`, { observe: 'response' });
    }

    convertAttachmentDatesFromClient(attachment: Attachment): Attachment {
        const copy: Attachment = Object.assign({}, attachment, {
            releaseDate: convertDateFromClient(attachment.releaseDate),
            uploadDate: convertDateFromClient(attachment.uploadDate),
        });
        return copy;
    }

    convertAttachmentResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.convertAttachmentDatesFromServer(res.body);
        }
        return res;
    }

    convertAttachmentDatesFromServer(attachment?: Attachment) {
        if (attachment) {
            attachment.releaseDate = convertDateFromServer(attachment.releaseDate);
            attachment.uploadDate = convertDateFromServer(attachment.uploadDate);
        }
        return attachment;
    }

    convertAttachmentArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((attachment: Attachment) => {
                this.convertAttachmentDatesFromServer(attachment);
            });
        }
        return res;
    }
}
