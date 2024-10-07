import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { GitDiffReportComponent } from './git-diff-report.component';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffFilePanelTitleComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel-title.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

@NgModule({
    imports: [ArtemisSharedModule, NgbAccordionModule, MonacoDiffEditorComponent, ArtemisSharedComponentModule],
    declarations: [GitDiffFilePanelComponent, GitDiffFilePanelTitleComponent, GitDiffReportComponent, GitDiffReportModalComponent, GitDiffLineStatComponent],
    exports: [GitDiffReportComponent, GitDiffReportModalComponent, GitDiffLineStatComponent],
})
export class GitDiffReportModule {}
