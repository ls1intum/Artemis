import { NgModule } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@NgModule({
    imports: [ArtemisMarkdownEditorModule],
    declarations: [HtmlForMarkdownPipe],
    providers: [ArtemisMarkdownService],
    exports: [HtmlForMarkdownPipe],
})
export class ArtemisMarkdownModule {}
