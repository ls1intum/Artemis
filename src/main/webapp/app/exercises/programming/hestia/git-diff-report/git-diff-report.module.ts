import { NgModule } from '@angular/core';
import { FullGitDiffEntryComponent } from 'app/exercises/programming/hestia/git-diff-report/full-git-diff-entry.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { FullGitDiffReportComponent } from './full-git-diff-report.component';
import { FullGitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/full-git-diff-report-modal.component';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule],
    declarations: [FullGitDiffEntryComponent, FullGitDiffReportComponent, FullGitDiffReportModalComponent],
    exports: [FullGitDiffReportComponent, FullGitDiffReportModalComponent],
})
export class GitDiffReportModule {}
