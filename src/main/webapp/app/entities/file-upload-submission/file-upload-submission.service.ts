import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IFileUploadSubmission } from 'app/shared/model/file-upload-submission.model';

type EntityResponseType = HttpResponse<IFileUploadSubmission>;
type EntityArrayResponseType = HttpResponse<IFileUploadSubmission[]>;

@Injectable({ providedIn: 'root' })
export class FileUploadSubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/file-upload-submissions';

    constructor(private http: HttpClient) {}

    create(fileUploadSubmission: IFileUploadSubmission): Observable<EntityResponseType> {
        return this.http.post<IFileUploadSubmission>(this.resourceUrl, fileUploadSubmission, { observe: 'response' });
    }

    update(fileUploadSubmission: IFileUploadSubmission): Observable<EntityResponseType> {
        return this.http.put<IFileUploadSubmission>(this.resourceUrl, fileUploadSubmission, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IFileUploadSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IFileUploadSubmission[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
