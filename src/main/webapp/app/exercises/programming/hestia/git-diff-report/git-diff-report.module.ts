import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { GitDiffReportComponent } from './git-diff-report.component';
import { GitDiffFileComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file.component';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { GitDiffFilePanelTitleComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel-title.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, NgbAccordionModule, MonacoEditorModule, ArtemisSharedComponentModule],
    declarations: [GitDiffFilePanelComponent, GitDiffFilePanelTitleComponent, GitDiffReportComponent, GitDiffFileComponent, GitDiffReportModalComponent, GitDiffLineStatComponent],
    exports: [GitDiffReportComponent, GitDiffReportModalComponent, GitDiffLineStatComponent],
})
export class GitDiffReportModule {}
