import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IFileUploadExercise } from 'app/shared/model/file-upload-exercise.model';

type EntityResponseType = HttpResponse<IFileUploadExercise>;
type EntityArrayResponseType = HttpResponse<IFileUploadExercise[]>;

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/file-upload-exercises';

    constructor(private http: HttpClient) {}

    create(fileUploadExercise: IFileUploadExercise): Observable<EntityResponseType> {
        return this.http.post<IFileUploadExercise>(this.resourceUrl, fileUploadExercise, { observe: 'response' });
    }

    update(fileUploadExercise: IFileUploadExercise): Observable<EntityResponseType> {
        return this.http.put<IFileUploadExercise>(this.resourceUrl, fileUploadExercise, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IFileUploadExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IFileUploadExercise[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
