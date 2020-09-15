import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [PlagiarismHeaderComponent, PlagiarismInspectorComponent, PlagiarismSplitViewComponent],
    exports: [PlagiarismHeaderComponent, PlagiarismInspectorComponent, PlagiarismSplitViewComponent],
})
export class ArtemisPlagiarismModule {}
