import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { Imprint } from 'app/entities/imprint.model';

@Injectable({
    providedIn: 'root',
})
export class ImprintService {
    constructor(private http: HttpClient) {}

    getImprint(language: LegalDocumentLanguage): Observable<Imprint> {
        return this.http.get(SERVER_API_URL + 'api/imprint', {
            params: new HttpParams().set('language', language),
        }) as Observable<Imprint>;
    }

    getImprintForUpdate(language: LegalDocumentLanguage): Observable<Imprint> {
        return this.http.get(SERVER_API_URL + 'api/imprint-for-update', {
            params: new HttpParams().set('language', language),
        }) as Observable<Imprint>;
    }

    updateImprint(imprint: Imprint): Observable<Imprint> {
        return this.http.put<Imprint>(SERVER_API_URL + 'api/imprint', imprint) as Observable<Imprint>;
    }
}
