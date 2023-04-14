import { Component, OnInit, ViewChild } from '@angular/core';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { PrivacyStatementService } from 'app/shared/service/privacy-statement.service';
import { PrivacyStatement, PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';
import { MarkdownEditorComponent, MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { PrivacyStatementUnsavedChangesWarningComponent } from 'app/admin/privacy-statement/unsaved-changes-warning/privacy-statement-unsaved-changes-warning.component';

@Component({
    selector: 'jhi-privacy-statement-update-component',
    styleUrls: ['./privacy-statement-update.component.scss'],
    templateUrl: './privacy-statement-update.component.html',
})
export class PrivacyStatementUpdateComponent implements OnInit {
    privacyStatement: PrivacyStatement;
    supportedLanguages: PrivacyStatementLanguage[] = [PrivacyStatementLanguage.GERMAN, PrivacyStatementLanguage.ENGLISH];
    unsavedChanges = false;
    faBan = faBan;
    faSave = faSave;
    isSaving = false;
    @ViewChild(MarkdownEditorComponent, { static: false }) markdownEditor: MarkdownEditorComponent;
    readonly languageOptions = this.supportedLanguages.map((language) => ({
        value: language,
        labelKey: 'artemisApp.privacyStatement.language.' + language,
        btnClass: 'btn-primary',
    }));
    readonly defaultLanguage = PrivacyStatementLanguage.GERMAN;
    readonly maxHeight = MarkdownEditorHeight.EXTRA_LARGE;
    readonly minHeight = MarkdownEditorHeight.MEDIUM;
    currentLanguage = this.defaultLanguage;
    unsavedChangesWarning: NgbModalRef;

    constructor(private privacyStatementService: PrivacyStatementService, private modalService: NgbModal) {}

    ngOnInit() {
        this.privacyStatement = new PrivacyStatement(this.defaultLanguage);
        this.privacyStatementService.getPrivacyStatementForUpdate(this.defaultLanguage).subscribe((statement) => {
            this.privacyStatement = statement;
        });
    }

    updatePrivacyStatement() {
        this.isSaving = true;
        this.privacyStatement.text = this.markdownEditor.markdown!;
        this.privacyStatementService.updatePrivacyStatement(this.privacyStatement).subscribe((statement) => {
            this.privacyStatement = statement;
            this.unsavedChanges = false;
            this.isSaving = false;
        });
    }

    checkUnsavedChanges(content: string) {
        if (content !== this.privacyStatement.text) {
            this.unsavedChanges = true;
        } else {
            this.unsavedChanges = false;
        }
    }

    onLanguageChange(privacyStatementLanguage: any) {
        if (this.unsavedChanges) {
            this.showWarning(privacyStatementLanguage);
        } else {
            this.currentLanguage = privacyStatementLanguage;
            this.markdownEditor.markdown = '';
            this.privacyStatementService.getPrivacyStatementForUpdate(this.currentLanguage).subscribe((statement) => (this.privacyStatement = statement));
        }
    }

    showWarning(privacyStatementLanguage: any) {
        this.unsavedChangesWarning = this.modalService.open(PrivacyStatementUnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
        this.unsavedChangesWarning.result.then(() => {
            this.unsavedChanges = false;
            this.onLanguageChange(privacyStatementLanguage);
        });
    }
}
