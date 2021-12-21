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
import { PlagiarismRunDetailsComponent } from './plagiarism-run-details/plagiarism-run-details.component';
import { NgxChartsModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisModelingEditorModule, FormsModule, RouterModule, TranslateModule, NgxChartsModule],
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
        PlagiarismRunDetailsComponent,
    ],
    exports: [PlagiarismInspectorComponent, PlagiarismSplitViewComponent],
})
export class ArtemisPlagiarismModule {}
