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

    create(attachment: Attachment): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(attachment);
        return this.http
            .post<Attachment>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(attachment: Attachment, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.convertDateFromClient(attachment);
        return this.http
            .put<Attachment>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Attachment>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Attachment[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    findAllByLectureId(lectureId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Attachment[]>(`api/lectures/${lectureId}/attachments`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    convertDateFromClient(attachment: Attachment): Attachment {
        const copy: Attachment = Object.assign({}, attachment, {
            releaseDate: attachment.releaseDate && attachment.releaseDate.isValid() ? attachment.releaseDate.toJSON() : undefined,
            uploadDate: attachment.uploadDate && attachment.uploadDate.isValid() ? attachment.uploadDate.toJSON() : undefined,
        });
        return copy;
    }

    convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate ? moment(res.body.releaseDate) : undefined;
            res.body.uploadDate = res.body.uploadDate ? moment(res.body.uploadDate) : undefined;
        }
        return res;
    }

    convertAttachmentDateFromServer(attachment?: Attachment) {
        if (attachment) {
            attachment.releaseDate = attachment.releaseDate ? moment(attachment.releaseDate) : undefined;
            attachment.uploadDate = attachment.uploadDate ? moment(attachment.uploadDate) : undefined;
        }
        return attachment;
    }

    convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((attachment: Attachment) => {
                attachment.releaseDate = attachment.releaseDate ? moment(attachment.releaseDate) : undefined;
                attachment.uploadDate = attachment.uploadDate ? moment(attachment.uploadDate) : undefined;
            });
        }
        return res;
    }
}
