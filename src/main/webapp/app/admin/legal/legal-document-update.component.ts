import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { TumUiButtonDirective, TumUiSelectButtonComponent } from '@tumaet/ui-angular';
import { UnsavedChangesWarningComponent } from 'app/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/admin/legal/legal-document.model';
import { ActivatedRoute } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';

@Component({
    selector: 'jhi-privacy-statement-update-component',
    templateUrl: './legal-document-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        MarkdownEditorMonacoComponent,
        FaIconComponent,
        TumUiButtonDirective,
        FormsModule,
        TumUiSelectButtonComponent,
        ArtemisTranslatePipe,
        AdminTitleBarTitleDirective,
        UnsavedChangesWarningComponent,
    ],
})
export class LegalDocumentUpdateComponent implements OnInit {
    private readonly legalDocumentService = inject(LegalDocumentService);
    private readonly route = inject(ActivatedRoute);
    private readonly languageHelper = inject(JhiLanguageHelper);

    protected readonly SUPPORTED_LANGUAGES: LegalDocumentLanguage[] = [LegalDocumentLanguage.GERMAN, LegalDocumentLanguage.ENGLISH];
    protected readonly faSave = faSave;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly LANGUAGE_OPTIONS = this.SUPPORTED_LANGUAGES.map((language) => ({
        value: language,
        labelKey: 'artemisApp.legal.language.' + language,
    }));
    protected readonly DEFAULT_LANGUAGE = LegalDocumentLanguage.GERMAN;
    protected readonly MAX_HEIGHT = MarkdownEditorHeight.EXTRA_LARGE;
    protected readonly MIN_HEIGHT = MarkdownEditorHeight.MEDIUM;

    readonly legalDocument = signal<LegalDocument | undefined>(undefined);

    legalDocumentType: LegalDocumentType = LegalDocumentType.PRIVACY_STATEMENT;

    readonly unsavedChanges = signal(false);

    readonly isSaving = signal(false);

    readonly currentContentTrimmed = signal('');

    readonly currentLanguage = signal(this.DEFAULT_LANGUAGE);

    readonly showUnsavedChangesWarning = signal(false);

    private pendingLanguageChange: LegalDocumentLanguage | undefined;

    readonly warningTextMessage = signal('');

    readonly titleKey = signal<string>(undefined!);

    ngOnInit() {
        this.route.url
            .pipe(
                tap((segments) => {
                    this.legalDocumentType = segments.some((segment) => segment.path === 'imprint') ? LegalDocumentType.IMPRINT : LegalDocumentType.PRIVACY_STATEMENT;
                }),
            )
            .subscribe();
        if (this.legalDocumentType === LegalDocumentType.IMPRINT) {
            this.titleKey.set('artemisApp.legal.imprint.updateImprint');
        } else {
            this.titleKey.set('artemisApp.legal.privacyStatement.updatePrivacyStatement');
        }
        this.languageHelper.updateTitle(this.titleKey());

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
        this.pendingLanguageChange = legalDocumentLanguage;
        if (this.legalDocumentType === LegalDocumentType.PRIVACY_STATEMENT) {
            this.warningTextMessage.set('artemisApp.legal.privacyStatement.unsavedChangesWarning');
        } else if (this.legalDocumentType === LegalDocumentType.IMPRINT) {
            this.warningTextMessage.set('artemisApp.legal.imprint.unsavedChangesWarning');
        } else {
            throw new Error('Unknown legal document type!');
        }
        this.showUnsavedChangesWarning.set(true);
    }

    onDiscardChanges() {
        this.unsavedChanges.set(false);
        if (this.pendingLanguageChange) {
            this.onLanguageChange(this.pendingLanguageChange);
            this.pendingLanguageChange = undefined;
        }
    }
}
