import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { PlagiarismSplitViewComponent, SplitPaneDirective } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { PlagiarismViewerComponent } from 'app/exercises/shared/plagiarism/plagiarism-viewer/plagiarism-viewer.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisModelingEditorModule, RouterModule, TranslateModule],
    declarations: [PlagiarismHeaderComponent, PlagiarismInspectorComponent, PlagiarismSplitViewComponent, PlagiarismViewerComponent, SplitPaneDirective],
    exports: [PlagiarismInspectorComponent],
})
export class ArtemisPlagiarismModule {}
