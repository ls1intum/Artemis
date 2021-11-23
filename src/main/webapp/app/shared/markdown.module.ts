import { NgModule } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';

@NgModule({
    imports: [ArtemisMarkdownEditorModule],
    providers: [ArtemisMarkdownService],
    exports: [MarkdownEditorComponent],
})
export class ArtemisMarkdownModule {}
