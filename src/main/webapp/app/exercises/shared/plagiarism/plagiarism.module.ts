import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [PlagiarismInspectorComponent],
    exports: [PlagiarismInspectorComponent],
})
export class ArtemisPlagiarismModule {}
