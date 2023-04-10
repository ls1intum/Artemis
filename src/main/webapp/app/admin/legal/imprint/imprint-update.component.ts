import { Component, OnInit, ViewChild } from '@angular/core';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { MarkdownEditorComponent, MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { UnsavedChangesWarningComponent } from 'app/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { ImprintService } from 'app/shared/service/imprint.service';
import { Imprint } from 'app/entities/imprint.model';

@Component({
    selector: 'jhi-imprint-update-component',
    styleUrls: ['./imprint-update.component.scss'],
    templateUrl: './imprint-update.component.html',
})
export class ImprintUpdateComponent implements OnInit {
    imprint: Imprint;
    supportedLanguages: LegalDocumentLanguage[] = [LegalDocumentLanguage.GERMAN, LegalDocumentLanguage.ENGLISH];
    unsavedChanges = false;
    faBan = faBan;
    faSave = faSave;
    isSaving = false;
    @ViewChild(MarkdownEditorComponent, { static: false }) markdownEditor: MarkdownEditorComponent;
    readonly languageOptions = this.supportedLanguages.map((language) => ({
        value: language,
        labelKey: 'artemisApp.imprint.language.' + language,
        btnClass: 'btn-primary',
    }));
    readonly defaultLanguage = LegalDocumentLanguage.GERMAN;
    readonly maxHeight = MarkdownEditorHeight.EXTRA_LARGE;
    readonly minHeight = MarkdownEditorHeight.MEDIUM;
    currentLanguage = this.defaultLanguage;
    unsavedChangesWarning: NgbModalRef;

    constructor(private imprintService: ImprintService, private modalService: NgbModal) {}

    ngOnInit() {
        this.imprint = new Imprint(this.defaultLanguage);
        this.imprintService.getImprintForUpdate(this.defaultLanguage).subscribe((imprint) => {
            this.imprint = imprint;
        });
    }

    updateImprint() {
        this.isSaving = true;
        this.imprint.text = this.markdownEditor.markdown!;
        this.imprintService.updateImprint(this.imprint).subscribe((imprint) => {
            this.imprint = imprint;
            this.unsavedChanges = false;
            this.isSaving = false;
        });
    }

    checkUnsavedChanges(content: string) {
        if (content !== this.imprint.text) {
            this.unsavedChanges = true;
        } else {
            this.unsavedChanges = false;
        }
    }

    onLanguageChange(imprintLanguage: any) {
        if (this.unsavedChanges) {
            this.showWarning(imprintLanguage);
        } else {
            this.currentLanguage = imprintLanguage;
            this.imprintService.getImprintForUpdate(imprintLanguage).subscribe((imprint) => (this.imprint = imprint));
        }
    }

    showWarning(imprintLanguage: any) {
        this.unsavedChangesWarning = this.modalService.open(UnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
        this.unsavedChangesWarning.componentInstance.textMessage = 'artemisApp.imprint.unsavedChangesWarning';
        this.unsavedChangesWarning.result.then(() => {
            this.unsavedChanges = false;
            this.onLanguageChange(imprintLanguage);
        });
    }
}
