import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { TestwiseCoverageReportModalComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report-modal.component';
import { TestwiseCoverageReportComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report.component';
import { TestwiseCoverageFileComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-file.component';
import { MatExpansionModule } from '@angular/material/expansion';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule, MatExpansionModule],
    declarations: [TestwiseCoverageFileComponent, TestwiseCoverageReportComponent, TestwiseCoverageReportModalComponent],
    exports: [TestwiseCoverageFileComponent, TestwiseCoverageReportModalComponent],
})
export class TestwiseCoverageReportModule {}
