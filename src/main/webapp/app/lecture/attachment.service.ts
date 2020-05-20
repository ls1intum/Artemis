import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
import { Attachment } from 'app/entities/attachment.model';

type EntityResponseType = HttpResponse<Attachment>;
type EntityArrayResponseType = HttpResponse<Attachment[]>;

@Injectable({ providedIn: 'root' })
export class AttachmentService {
    public resourceUrl = SERVER_API_URL + 'api/attachments';

    constructor(protected http: HttpClient) {}

    /**
     * Adds the given attachment.
     * @param attachment that should be added of type {Attachment}
     * @returns {Observable<HttpResponse<Attachment>>}
     */
    create(attachment: Attachment): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(attachment);
        return this.http
            .post<Attachment>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Updates the given attachment.
     * @param attachment that should be updated of type {Attachment}
     * @param req request options
     * @returns {Observable<HttpResponse<Attachment>>}
     */
    update(attachment: Attachment, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.convertDateFromClient(attachment);
        return this.http
            .put<Attachment>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Retrieves the attachment with the given id.
     * @param id of the attachment that should be retrieved of type {number}
     * @returns {Observable<HttpResponse<Attachment>>}
     */
    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Attachment>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Query all attachments.
     * @param req request options
     * @returns {Observable<HttpResponse<Attachment[]>>}
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Attachment[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * Find all attachments that belong to a given lecture.
     * @param lectureId id of the lecture for which all attachments should be retrieved of type {number}
     * @returns {Observable<HttpResponse<Attachment[]>>}
     */
    findAllByLectureId(lectureId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Attachment[]>(`api/lectures/${lectureId}/attachments`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * Deletes the attachment with the given id.
     * @param id of the attachment that should be deleted of type {number}
     * @returns {Observable<HttpResponse<any>>}
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    protected convertDateFromClient(attachment: Attachment): Attachment {
        const copy: Attachment = Object.assign({}, attachment, {
            releaseDate: attachment.releaseDate != null && attachment.releaseDate.isValid() ? attachment.releaseDate.toJSON() : null,
            uploadDate: attachment.uploadDate != null && attachment.uploadDate.isValid() ? attachment.uploadDate.toJSON() : null,
        });
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate != null ? moment(res.body.releaseDate) : null;
            res.body.uploadDate = res.body.uploadDate != null ? moment(res.body.uploadDate) : null;
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((attachment: Attachment) => {
                attachment.releaseDate = attachment.releaseDate != null ? moment(attachment.releaseDate) : null;
                attachment.uploadDate = attachment.uploadDate != null ? moment(attachment.uploadDate) : null;
            });
        }
        return res;
    }
}
