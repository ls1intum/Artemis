import { NgModule } from '@angular/core';
import { GitDiffEntryComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-entry.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { GitDiffReportComponent } from './git-diff-report.component';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule],
    declarations: [GitDiffEntryComponent, GitDiffReportComponent, GitDiffReportModalComponent],
    exports: [GitDiffReportComponent, GitDiffReportModalComponent],
})
export class GitDiffReportModule {}
