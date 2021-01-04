import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-details/plagiarism-details.component';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { PlagiarismSplitViewComponent, SplitPaneDirective } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ModelingSubmissionViewerComponent } from './plagiarism-split-view/modeling-submission-viewer/modeling-submission-viewer.component';
import { TextSubmissionViewerComponent } from './plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';
import { SplitPaneHeaderComponent } from './plagiarism-split-view/split-pane-header/split-pane-header.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisModelingEditorModule, FormsModule, RouterModule, TranslateModule],
    declarations: [
        PlagiarismDetailsComponent,
        PlagiarismHeaderComponent,
        PlagiarismInspectorComponent,
        PlagiarismSidebarComponent,
        PlagiarismSplitViewComponent,
        SplitPaneDirective,
        ModelingSubmissionViewerComponent,
        TextSubmissionViewerComponent,
        SplitPaneHeaderComponent,
    ],
    exports: [PlagiarismInspectorComponent],
})
export class ArtemisPlagiarismModule {}
