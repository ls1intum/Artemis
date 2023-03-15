import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Audit } from 'app/admin/audits/audit.model';
import { createRequestOption } from 'app/shared/util/request.util';

@Injectable({ providedIn: 'root' })
export class AuditsService {
    constructor(private http: HttpClient) {}

    query(req: any): Observable<HttpResponse<Audit[]>> {
        const params: HttpParams = createRequestOption(req);

        const requestURL = SERVER_API_URL + 'api/admin/audits';

        return this.http.get<Audit[]>(requestURL, {
            params,
            observe: 'response',
        });
    }
}
