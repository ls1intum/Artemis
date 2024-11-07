import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TestwiseCoverageReportModalComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report-modal.component';
import { TestwiseCoverageReportComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report.component';
import { TestwiseCoverageFileComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-file.component';
import { MatExpansionModule } from '@angular/material/expansion';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@NgModule({
    imports: [ArtemisSharedModule, MatExpansionModule, MonacoEditorComponent],
    declarations: [TestwiseCoverageFileComponent, TestwiseCoverageReportComponent, TestwiseCoverageReportModalComponent],
    exports: [TestwiseCoverageFileComponent, TestwiseCoverageReportModalComponent, TestwiseCoverageReportComponent],
})
export class TestwiseCoverageReportModule {}
