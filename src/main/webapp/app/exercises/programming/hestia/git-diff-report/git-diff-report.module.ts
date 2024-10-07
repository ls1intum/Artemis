import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

@NgModule({
    imports: [ArtemisSharedModule, NgbAccordionModule, MonacoDiffEditorComponent, ArtemisSharedComponentModule],
    declarations: [],
    exports: [],
})
export class GitDiffReportModule {}
