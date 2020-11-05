import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';

type EntityResponseType = HttpResponse<TextUnit>;
type EntityArrayResponseType = HttpResponse<TextUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class TextUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}
}
