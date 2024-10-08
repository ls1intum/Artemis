import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { createRequestOption } from 'app/shared/util/request.util';
import { Audit } from 'app/admin/audits/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditsService {
    private http = inject(HttpClient);

    query(req: any): Observable<HttpResponse<Audit[]>> {
        const params: HttpParams = createRequestOption(req);

        const requestURL = 'api/admin/audits';

        return this.http.get<Audit[]>(requestURL, {
            params,
            observe: 'response',
        });
    }
}
