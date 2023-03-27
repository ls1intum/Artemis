import { AfterViewInit, Component, OnInit } from '@angular/core';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { PrivacyStatementService } from 'app/admin/privacy-statement/privacy-statement.service';
import { StaticContentService } from 'app/shared/service/static-content.service';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { PrivacyStatement, PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';
import { Router } from '@angular/router';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';

@Component({
    selector: 'jhi-privacy-statement-update-component',
    templateUrl: './privacy-statement-update.component.html',
})
export class PrivacyStatementUpdateComponent implements AfterViewInit, OnInit {
    privacyStatement: PrivacyStatement;
    readonly maxHeight = MarkdownEditorHeight.EXTRA_LARGE;
    readonly minHeight = MarkdownEditorHeight.MEDIUM;
    unsavedChanges = false;
    submitButtonTitle: string;
    faBan = faBan;
    faSave = faSave;

    constructor(private privacyStatementService: PrivacyStatementService, private staticContentService: StaticContentService, private router: Router) {}

    ngOnInit() {
        this.submitButtonTitle = 'entity.action.save';
        this.privacyStatement = new PrivacyStatement();
        this.privacyStatement.language = PrivacyStatementLanguage.ENGLISH;
    }

    ngAfterViewInit(): void {
        this.privacyStatementService.getPrivacyStatement(this.privacyStatement.language).subscribe((statement) => (this.privacyStatement = statement));
    }

    updatePrivacyStatement() {
        this.privacyStatementService.updatePrivacyStatement(this.privacyStatement);
        this.unsavedChanges = false;
    }

    navigateBack() {
        if (this.unsavedChanges) {
            //abc
        } else {
            // send the user back to the home page as there is no other suitable target
            this.router.navigate(['courses', 'overview']);
        }
    }

    checkUnsavedChanges(content: string) {
        if (content !== this.privacyStatement.text) {
            this.unsavedChanges = true;
        } else {
            this.unsavedChanges = false;
        }
    }
}
