import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { PrivacyStatement } from 'app/entities/privacy-statement.model';
import { Observable } from 'rxjs';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/entities/legal-document.model';
import { Imprint } from 'app/entities/imprint.model';

@Injectable({
    providedIn: 'root',
})
export class LegalDocumentService {
    private resourceUrlPrivacyStatement = 'api/privacy-statement';
    private resourceUrlPrivacyStatementForUpdate = 'api/privacy-statement-for-update';
    private resourceUrlImprint = 'api/imprint';
    private resourceUrlImprintForUpdate = 'api/imprint-for-update';

    constructor(private http: HttpClient) {}

    getPrivacyStatement(language: LegalDocumentLanguage): Observable<PrivacyStatement> {
        return this.getRequestToResource(language, LegalDocumentType.PRIVACY_STATEMENT) as Observable<PrivacyStatement>;
    }

    getPrivacyStatementForUpdate(language: LegalDocumentLanguage): Observable<PrivacyStatement> {
        return this.getRequestForUpdateToResource(language, LegalDocumentType.PRIVACY_STATEMENT) as Observable<PrivacyStatement>;
    }

    updatePrivacyStatement(privacyStatement: PrivacyStatement): Observable<PrivacyStatement> {
        return this.putRequestToResource(privacyStatement, LegalDocumentType.PRIVACY_STATEMENT) as Observable<PrivacyStatement>;
    }

    getImprint(language: LegalDocumentLanguage): Observable<Imprint> {
        return this.getRequestToResource(language, LegalDocumentType.IMPRINT) as Observable<Imprint>;
    }

    getImprintForUpdate(language: LegalDocumentLanguage): Observable<Imprint> {
        return this.getRequestForUpdateToResource(language, LegalDocumentType.IMPRINT) as Observable<Imprint>;
    }

    updateImprint(imprint: Imprint): Observable<Imprint> {
        return this.putRequestToResource(imprint, LegalDocumentType.IMPRINT) as Observable<Imprint>;
    }

    private getRequestToResource(language: LegalDocumentLanguage, type: LegalDocumentType): Observable<LegalDocument> {
        let resourceUrl = this.resourceUrlPrivacyStatement;
        if (type === LegalDocumentType.IMPRINT) {
            resourceUrl = this.resourceUrlImprint;
        }
        return this.http.get<LegalDocument>(resourceUrl, {
            params: new HttpParams().set('language', language),
        });
    }

    private getRequestForUpdateToResource(language: LegalDocumentLanguage, type: LegalDocumentType): Observable<LegalDocument> {
        let resourceUrl = this.resourceUrlPrivacyStatementForUpdate;
        if (type === LegalDocumentType.IMPRINT) {
            resourceUrl = this.resourceUrlImprintForUpdate;
        }
        return this.http.get<LegalDocument>(resourceUrl, {
            params: new HttpParams().set('language', language),
        });
    }

    private putRequestToResource(legalDocument: LegalDocument, type: LegalDocumentType): Observable<LegalDocument> {
        let resourceUrl = this.resourceUrlPrivacyStatement;
        if (type === LegalDocumentType.IMPRINT) {
            resourceUrl = this.resourceUrlImprint;
        }
        return this.http.put(resourceUrl, legalDocument) as Observable<LegalDocument>;
    }
}
