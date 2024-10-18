import { AfterContentChecked, ChangeDetectorRef, Component, OnInit, ViewChild, inject } from '@angular/core';
import { faBan, faCheckCircle, faCircleNotch, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { UnsavedChangesWarningComponent } from 'app/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/entities/legal-document.model';
import { ActivatedRoute } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

@Component({
    selector: 'jhi-privacy-statement-update-component',
    styleUrls: ['./legal-document-update.component.scss'],
    templateUrl: './legal-document-update.component.html',
})
export class LegalDocumentUpdateComponent implements OnInit, AfterContentChecked {
    private legalDocumentService = inject(LegalDocumentService);
    private modalService = inject(NgbModal);
    private route = inject(ActivatedRoute);
    private languageHelper = inject(JhiLanguageHelper);
    private changeDetectorRef = inject(ChangeDetectorRef);

    readonly SUPPORTED_LANGUAGES: LegalDocumentLanguage[] = [LegalDocumentLanguage.GERMAN, LegalDocumentLanguage.ENGLISH];
    readonly faBan = faBan;
    readonly faSave = faSave;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faCheckCircle = faCheckCircle;
    readonly faCircleNotch = faCircleNotch;
    readonly LANGUAGE_OPTIONS = this.SUPPORTED_LANGUAGES.map((language) => ({
        value: language,
        labelKey: 'artemisApp.legal.language.' + language,
        btnClass: 'btn-primary',
    }));
    readonly DEFAULT_LANGUAGE = LegalDocumentLanguage.GERMAN;
    readonly MAX_HEIGHT = MarkdownEditorHeight.EXTRA_LARGE;
    readonly MIN_HEIGHT = MarkdownEditorHeight.MEDIUM;

    legalDocument: LegalDocument;
    legalDocumentType: LegalDocumentType = LegalDocumentType.PRIVACY_STATEMENT;
    unsavedChanges = false;
    isSaving = false;
    @ViewChild(MarkdownEditorMonacoComponent, { static: false }) markdownEditor: MarkdownEditorMonacoComponent;

    currentContentTrimmed = '';
    currentLanguage = this.DEFAULT_LANGUAGE;
    unsavedChangesWarning: NgbModalRef;
    titleKey: string;

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
            this.titleKey = 'artemisApp.legal.imprint.updateImprint';
        } else {
            this.titleKey = 'artemisApp.legal.privacyStatement.updatePrivacyStatement';
        }
        this.languageHelper.updateTitle(this.titleKey);

        this.legalDocument = new LegalDocument(this.legalDocumentType, this.DEFAULT_LANGUAGE);
        this.getLegalDocumentForUpdate(this.legalDocumentType, this.DEFAULT_LANGUAGE).subscribe((document) => {
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
        this.legalDocument.text = this.currentContentTrimmed;
        if (this.legalDocumentType === LegalDocumentType.PRIVACY_STATEMENT) {
            this.legalDocumentService.updatePrivacyStatement(this.legalDocument).subscribe((statement) => {
                this.setUpdatedDocument(statement);
            });
        } else {
            this.legalDocumentService.updateImprint(this.legalDocument).subscribe((imprint) => {
                this.setUpdatedDocument(imprint);
            });
        }
    }

    private setUpdatedDocument(updatedDocument: LegalDocument) {
        this.legalDocument = updatedDocument;
        this.unsavedChanges = false;
        this.isSaving = false;
    }

    onContentChanged(content: string) {
        this.currentContentTrimmed = content.trim();
        this.unsavedChanges = content !== this.legalDocument.text;
    }

    onLanguageChange(legalDocumentLanguage: LegalDocumentLanguage) {
        if (this.unsavedChanges) {
            this.showWarning(legalDocumentLanguage);
        } else {
            this.currentLanguage = legalDocumentLanguage;
            this.getLegalDocumentForUpdate(this.legalDocumentType, legalDocumentLanguage).subscribe((document) => {
                this.legalDocument = document;
                this.markdownEditor.markdown = this.legalDocument.text;
                // Ensure the new text is parsed and displayed in the preview
                this.markdownEditor.parseMarkdown();
                this.unsavedChanges = false;
            });
        }
    }

    showWarning(legalDocumentLanguage: LegalDocumentLanguage) {
        this.unsavedChangesWarning = this.modalService.open(UnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
        if (this.legalDocumentType === LegalDocumentType.PRIVACY_STATEMENT) {
            this.unsavedChangesWarning.componentInstance.textMessage = 'artemisApp.legal.privacyStatement.unsavedChangesWarning';
        } else if (this.legalDocumentType === LegalDocumentType.IMPRINT) {
            this.unsavedChangesWarning.componentInstance.textMessage = 'artemisApp.legal.imprint.unsavedChangesWarning';
        } else {
            throw new Error('Unknown legal document type!');
        }

        this.unsavedChangesWarning.result
            .then(() => {
                this.unsavedChanges = false;
                this.onLanguageChange(legalDocumentLanguage);
            })
            .catch(() => {
                //ignore, just prevents the console from logging an error about a rejected promise
            });
    }

    /**
     * This lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when dismissing all changes in the markdown editor
     * on dismissing the unsaved changes warning modal -> we do not want to store changes in the legal document that are not saved
     * */
    ngAfterContentChecked() {
        this.changeDetectorRef.detectChanges();
    }
}
