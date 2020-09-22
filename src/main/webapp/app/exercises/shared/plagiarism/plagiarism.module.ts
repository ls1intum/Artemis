import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { PlagiarismSplitViewComponent, SplitPaneDirective } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisModelingEditorModule],
    declarations: [PlagiarismHeaderComponent, PlagiarismInspectorComponent, PlagiarismSplitViewComponent, SplitPaneDirective],
    exports: [PlagiarismHeaderComponent, PlagiarismInspectorComponent, PlagiarismSplitViewComponent],
})
export class ArtemisPlagiarismModule {}
