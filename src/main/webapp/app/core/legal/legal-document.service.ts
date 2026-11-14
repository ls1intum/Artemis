import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Imprint } from 'app/admin/legal/imprint.model';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/admin/legal/legal-document.model';
import { PrivacyStatement } from 'app/admin/legal/privacy-statement.model';
import { AdminImprintApi } from 'app/openapi/api/admin-imprint-api';
import { AdminPrivacyStatementApi } from 'app/openapi/api/admin-privacy-statement-api';
import { PublicImprintApi } from 'app/openapi/api/public-imprint-api';
import { PublicPrivacyStatementApi } from 'app/openapi/api/public-privacy-statement-api';
import type { Imprint as ImprintDto } from 'app/openapi/model/imprint';
import type { PrivacyStatement as PrivacyStatementDto } from 'app/openapi/model/privacy-statement';

type LegalDocumentDto = ImprintDto | PrivacyStatementDto;

const languageFromWire: Record<LegalDocumentDto['language'], LegalDocumentLanguage> = {
    ENGLISH: LegalDocumentLanguage.ENGLISH,
    GERMAN: LegalDocumentLanguage.GERMAN,
};

const languageToWire: Record<LegalDocumentLanguage, LegalDocumentDto['language']> = {
    [LegalDocumentLanguage.ENGLISH]: 'ENGLISH',
    [LegalDocumentLanguage.GERMAN]: 'GERMAN',
};

const typeFromWire: Record<LegalDocumentDto['type'], LegalDocumentType> = {
    IMPRINT: LegalDocumentType.IMPRINT,
    PRIVACY_STATEMENT: LegalDocumentType.PRIVACY_STATEMENT,
};

const typeToWire: Record<LegalDocumentType, LegalDocumentDto['type']> = {
    [LegalDocumentType.IMPRINT]: 'IMPRINT',
    [LegalDocumentType.PRIVACY_STATEMENT]: 'PRIVACY_STATEMENT',
};

function fromDto<T extends LegalDocument>(dto: LegalDocumentDto, Document: new (language: LegalDocumentLanguage) => T): T {
    const document = new Document(languageFromWire[dto.language]);
    document.type = typeFromWire[dto.type];
    document.text = dto.text;
    return document;
}

function toSaveDto(document: LegalDocument, text: string): Required<LegalDocumentDto> {
    return { type: typeToWire[document.type], language: languageToWire[document.language], text };
}

@Service()
export class LegalDocumentService {
    private readonly publicImprintApi = inject(PublicImprintApi);
    private readonly publicPrivacyStatementApi = inject(PublicPrivacyStatementApi);
    private readonly adminImprintApi = inject(AdminImprintApi);
    private readonly adminPrivacyStatementApi = inject(AdminPrivacyStatementApi);

    getPrivacyStatement(language: LegalDocumentLanguage): Observable<PrivacyStatement> {
        return this.publicPrivacyStatementApi.getPrivacyStatement(language).pipe(map((dto) => fromDto(dto, PrivacyStatement)));
    }

    getPrivacyStatementForUpdate(language: LegalDocumentLanguage): Observable<PrivacyStatement> {
        return this.adminPrivacyStatementApi.getPrivacyStatementForUpdate(language).pipe(map((dto) => fromDto(dto, PrivacyStatement)));
    }

    updatePrivacyStatement(document: LegalDocument, text: string): Observable<PrivacyStatement> {
        return this.adminPrivacyStatementApi.updatePrivacyStatement(toSaveDto(document, text)).pipe(map((dto) => fromDto(dto, PrivacyStatement)));
    }

    getImprint(language: LegalDocumentLanguage): Observable<Imprint> {
        return this.publicImprintApi.getImprint(language).pipe(map((dto) => fromDto(dto, Imprint)));
    }

    getImprintForUpdate(language: LegalDocumentLanguage): Observable<Imprint> {
        return this.adminImprintApi.getImprintForUpdate(language).pipe(map((dto) => fromDto(dto, Imprint)));
    }

    updateImprint(document: LegalDocument, text: string): Observable<Imprint> {
        return this.adminImprintApi.updateImprint(toSaveDto(document, text)).pipe(map((dto) => fromDto(dto, Imprint)));
    }
}
