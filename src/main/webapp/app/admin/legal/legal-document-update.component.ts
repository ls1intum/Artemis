import { Component, OnInit, ViewChild } from '@angular/core';
import { faBan, faCheckCircle, faCircleNotch, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { MarkdownEditorComponent, MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { UnsavedChangesWarningComponent } from 'app/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/entities/legal-document.model';
import { ActivatedRoute } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

@Component({
    selector: 'jhi-privacy-statement-update-component',
    styleUrls: ['./legal-document-update.component.scss'],
    templateUrl: './legal-document-update.component.html',
})
export class LegalDocumentUpdateComponent implements OnInit {
    readonly supportedLanguages: LegalDocumentLanguage[] = [LegalDocumentLanguage.GERMAN, LegalDocumentLanguage.ENGLISH];
    readonly faBan = faBan;
    readonly faSave = faSave;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faCheckCircle = faCheckCircle;
    readonly faCircleNotch = faCircleNotch;
    readonly languageOptions = this.supportedLanguages.map((language) => ({
        value: language,
        labelKey: 'artemisApp.legal.language.' + language,
        btnClass: 'btn-primary',
    }));
    readonly defaultLanguage = LegalDocumentLanguage.GERMAN;
    readonly maxHeight = MarkdownEditorHeight.EXTRA_LARGE;
    readonly minHeight = MarkdownEditorHeight.MEDIUM;

    legalDocument: LegalDocument;
    legalDocumentType: LegalDocumentType = LegalDocumentType.PRIVACY_STATEMENT;
    unsavedChanges = false;
    isSaving = false;
    @ViewChild(MarkdownEditorComponent, { static: false }) markdownEditor: MarkdownEditorComponent;

    currentLanguage = this.defaultLanguage;
    unsavedChangesWarning: NgbModalRef;
    titleKey: string;

    constructor(private legalDocumentService: LegalDocumentService, private modalService: NgbModal, private route: ActivatedRoute, private languageHelper: JhiLanguageHelper) {}

    ngOnInit() {
        // Tap the URL to determine, if it's the imprint or the privacy statement
        // we need the parent URL, because the imprint and privacy statement are children of the admin component and their path is specified there because they are lazy loaded
        this.route.parent?.url
            .pipe(
                tap((segments) => {
                    this.legalDocumentType = segments.some((segment) => segment.path === 'imprint') ? LegalDocumentType.IMPRINT : LegalDocumentType.PRIVACY_STATEMENT;
                }),
            )
            .subscribe();
        if (this.legalDocumentType === LegalDocumentType.IMPRINT) {
            this.languageHelper.updateTitle('artemisApp.legal.imprint.updateImprint');
            this.titleKey = 'artemisApp.legal.imprint.updateImprint';
        } else {
            this.languageHelper.updateTitle('artemisApp.legal.privacyStatement.updatePrivacyStatement');
            this.titleKey = 'artemisApp.legal.privacyStatement.updatePrivacyStatement';
        }
        this.legalDocument = new LegalDocument(this.legalDocumentType, this.defaultLanguage);
        this.getLegalDocumentForUpdate(this.legalDocumentType, this.defaultLanguage).subscribe((document) => {
            this.legalDocument = document;
        });
    }

    private getLegalDocumentForUpdate(type: LegalDocumentType, language: LegalDocumentLanguage): Observable<LegalDocument> {
        if (type === LegalDocumentType.PRIVACY_STATEMENT) {
            return this.legalDocumentService.getPrivacyStatementForUpdate(language);
        } else {
            return this.legalDocumentService.getImprintForUpdate(language);
        }
    }

    updateLegalDocument() {
        this.isSaving = true;
        this.legalDocument.text = this.markdownEditor.markdown!;
        if (this.legalDocumentType === LegalDocumentType.PRIVACY_STATEMENT) {
            this.legalDocumentService.updatePrivacyStatement(this.legalDocument).subscribe((statement) => {
                this.legalDocument = statement;
                this.unsavedChanges = false;
                this.isSaving = false;
            });
        } else {
            this.legalDocumentService.updateImprint(this.legalDocument).subscribe((statement) => {
                this.legalDocument = statement;
                this.unsavedChanges = false;
                this.isSaving = false;
            });
        }
    }

    checkUnsavedChanges(content: string) {
        this.unsavedChanges = content !== this.legalDocument.text;
    }

    onLanguageChange(legalDocumentLanguage: any) {
        if (this.unsavedChanges) {
            this.showWarning(legalDocumentLanguage);
        } else {
            this.currentLanguage = legalDocumentLanguage;
            this.getLegalDocumentForUpdate(this.legalDocumentType, legalDocumentLanguage).subscribe((document) => (this.legalDocument = document));
        }
    }

    showWarning(legalDocumentLanguage: any) {
        this.unsavedChangesWarning = this.modalService.open(UnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
        if (this.legalDocumentType === LegalDocumentType.PRIVACY_STATEMENT) {
            this.unsavedChangesWarning.componentInstance.textMessage = 'artemisApp.legal.privacyStatement.unsavedChangesWarning';
        } else {
            this.unsavedChangesWarning.componentInstance.textMessage = 'artemisApp.legal.imprint.unsavedChangesWarning';
        }

        this.unsavedChangesWarning.result.then(() => {
            this.unsavedChanges = false;
            this.onLanguageChange(legalDocumentLanguage);
        });
    }
}
