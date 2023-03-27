import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { PrivacyStatement, PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';
import { Observable, of } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class PrivacyStatementService {
    constructor(private http: HttpClient) {}

    updatePrivacyStatement(privacyStatement: PrivacyStatement) {}

    getPrivacyStatement(language: PrivacyStatementLanguage): Observable<PrivacyStatement> {
        return this.http.get(SERVER_API_URL + 'api/privacy-statement', {
            params: new HttpParams().set('language', language),
        }) as Observable<PrivacyStatement>;
    }
}
