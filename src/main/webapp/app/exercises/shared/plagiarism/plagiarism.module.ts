import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-details/plagiarism-details.component';
import { PlagiarismHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-header/plagiarism-header.component';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { PlagiarismSplitViewComponent, SplitPaneDirective } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisModelingEditorModule, RouterModule, TranslateModule],
    declarations: [
        PlagiarismDetailsComponent,
        PlagiarismHeaderComponent,
        PlagiarismInspectorComponent,
        PlagiarismSidebarComponent,
        PlagiarismSplitViewComponent,
        SplitPaneDirective,
    ],
    exports: [PlagiarismInspectorComponent],
})
export class ArtemisPlagiarismModule {}
