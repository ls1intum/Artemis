import { ChangeDetectionStrategy, Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { faBan, faCheckCircle, faCircleNotch, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { UnsavedChangesWarningComponent } from 'app/core/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/core/shared/entities/legal-document.model';
import { ActivatedRoute } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ModePickerComponent } from 'app/exercise/mode-picker/mode-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Admin component for updating legal documents (privacy statement and imprint).
 * Supports multiple languages and markdown editing.
 */
@Component({
    selector: 'jhi-privacy-statement-update-component',
    styleUrls: ['./legal-document-update.component.scss'],
    templateUrl: './legal-document-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, MarkdownEditorMonacoComponent, FaIconComponent, NgbTooltip, ModePickerComponent, ArtemisTranslatePipe],
})
export class LegalDocumentUpdateComponent implements OnInit {
    private readonly legalDocumentService = inject(LegalDocumentService);
    private readonly modalService = inject(NgbModal);
    private readonly route = inject(ActivatedRoute);
    private readonly languageHelper = inject(JhiLanguageHelper);

    protected readonly SUPPORTED_LANGUAGES: LegalDocumentLanguage[] = [LegalDocumentLanguage.GERMAN, LegalDocumentLanguage.ENGLISH];
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly LANGUAGE_OPTIONS = this.SUPPORTED_LANGUAGES.map((language) => ({
        value: language,
        labelKey: 'artemisApp.legal.language.' + language,
        btnClass: 'btn-primary',
    }));
    protected readonly DEFAULT_LANGUAGE = LegalDocumentLanguage.GERMAN;
    protected readonly MAX_HEIGHT = MarkdownEditorHeight.EXTRA_LARGE;
    protected readonly MIN_HEIGHT = MarkdownEditorHeight.MEDIUM;

    /** The legal document being edited */
    readonly legalDocument = signal<LegalDocument | undefined>(undefined);

    /** The type of legal document (privacy statement or imprint) */
    legalDocumentType: LegalDocumentType = LegalDocumentType.PRIVACY_STATEMENT;

    /** Whether there are unsaved changes */
    readonly unsavedChanges = signal(false);

    /** Whether the document is currently being saved */
    readonly isSaving = signal(false);

    /** Reference to the markdown editor component */
    readonly markdownEditor = viewChild(MarkdownEditorMonacoComponent);

    /** Current trimmed content from the editor */
    readonly currentContentTrimmed = signal('');

    /** Currently selected language */
    readonly currentLanguage = signal(this.DEFAULT_LANGUAGE);

    /** Modal reference for unsaved changes warning */
    unsavedChangesWarning: NgbModalRef;

    /** Translation key for the page title */
    titleKey: string;

    ngOnInit() {
        // Tap the URL to determine, if it's the imprint or the privacy statement
        // we need the parent URL, because the imprint and privacy statement are children of the admin component and their path is specified there because they are lazy loaded
        this.route.url
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

        this.legalDocument.set(new LegalDocument(this.legalDocumentType, this.DEFAULT_LANGUAGE));
        this.getLegalDocumentForUpdate(this.legalDocumentType, this.DEFAULT_LANGUAGE).subscribe((document) => {
            this.legalDocument.set(document);
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
        const doc = this.legalDocument();
        if (!doc) {
            return;
        }
        this.isSaving.set(true);
        doc.text = this.currentContentTrimmed();
        if (this.legalDocumentType === LegalDocumentType.PRIVACY_STATEMENT) {
            this.legalDocumentService.updatePrivacyStatement(doc).subscribe((statement) => {
                this.setUpdatedDocument(statement);
            });
        } else {
            this.legalDocumentService.updateImprint(doc).subscribe((imprint) => {
                this.setUpdatedDocument(imprint);
            });
        }
    }

    private setUpdatedDocument(updatedDocument: LegalDocument) {
        this.legalDocument.set(updatedDocument);
        this.unsavedChanges.set(false);
        this.isSaving.set(false);
    }

    onContentChanged(content: string) {
        this.currentContentTrimmed.set(content.trim());
        const doc = this.legalDocument();
        this.unsavedChanges.set(content !== doc?.text);
    }

    onLanguageChange(legalDocumentLanguage: LegalDocumentLanguage) {
        if (this.unsavedChanges()) {
            this.showWarning(legalDocumentLanguage);
        } else {
            this.currentLanguage.set(legalDocumentLanguage);
            this.getLegalDocumentForUpdate(this.legalDocumentType, legalDocumentLanguage).subscribe((document) => {
                this.legalDocument.set(document);
                this.unsavedChanges.set(false);
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
                this.unsavedChanges.set(false);
                this.onLanguageChange(legalDocumentLanguage);
            })
            .catch(() => {
                // Ignore - prevents console error about rejected promise
            });
    }
}
