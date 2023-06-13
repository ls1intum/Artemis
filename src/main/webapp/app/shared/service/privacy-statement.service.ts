import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { PrivacyStatement, PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class PrivacyStatementService {
    constructor(private http: HttpClient) {}

    getPrivacyStatement(language: PrivacyStatementLanguage): Observable<PrivacyStatement> {
        return this.http.get<PrivacyStatement>('api/public/privacy-statement', {
            params: new HttpParams().set('language', language),
        });
    }

    getPrivacyStatementForUpdate(language: PrivacyStatementLanguage): Observable<PrivacyStatement> {
        return this.http.get<PrivacyStatement>('api/privacy-statement-for-update', {
            params: new HttpParams().set('language', language),
        });
    }

    updatePrivacyStatement(privacyStatement: PrivacyStatement): Observable<PrivacyStatement> {
        return this.http.put<PrivacyStatement>('api/privacy-statement', privacyStatement);
    }
}
