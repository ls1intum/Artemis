import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GitDiffReportComponent } from './git-diff-report.component';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

@NgModule({
    imports: [ArtemisSharedModule, NgbAccordionModule, MonacoDiffEditorComponent, ArtemisSharedComponentModule],
    declarations: [GitDiffReportComponent, GitDiffReportModalComponent],
    exports: [GitDiffReportComponent, GitDiffReportModalComponent],
})
export class GitDiffReportModule {}
