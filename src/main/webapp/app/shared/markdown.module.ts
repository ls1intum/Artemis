import { NgModule } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

@NgModule({
    imports: [ArtemisMarkdownEditorModule],
    providers: [ArtemisMarkdownService],
})
export class ArtemisMarkdownModule {}
