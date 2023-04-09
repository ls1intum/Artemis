import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { PrivacyStatement } from 'app/entities/privacy-statement.model';
import { Observable } from 'rxjs';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';

@Injectable({
    providedIn: 'root',
})
export class PrivacyStatementService {
    constructor(private http: HttpClient) {}

    getPrivacyStatement(language: LegalDocumentLanguage): Observable<PrivacyStatement> {
        return this.http.get(SERVER_API_URL + 'api/privacy-statement', {
            params: new HttpParams().set('language', language),
        }) as Observable<PrivacyStatement>;
    }

    getPrivacyStatementForUpdate(language: LegalDocumentLanguage): Observable<PrivacyStatement> {
        return this.http.get(SERVER_API_URL + 'api/privacy-statement-for-update', {
            params: new HttpParams().set('language', language),
        }) as Observable<PrivacyStatement>;
    }

    updatePrivacyStatement(privacyStatement: PrivacyStatement): Observable<PrivacyStatement> {
        return this.http.put<PrivacyStatement>(SERVER_API_URL + 'api/privacy-statement', privacyStatement) as Observable<PrivacyStatement>;
    }
}
