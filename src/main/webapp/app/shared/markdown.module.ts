import { NgModule } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { HtmlForGuidedTourMarkdownPipe } from 'app/shared/pipes/html-for-guided-tour-markdown.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';

@NgModule({
    imports: [ArtemisMarkdownEditorModule],
    declarations: [HtmlForMarkdownPipe, HtmlForGuidedTourMarkdownPipe],
    providers: [ArtemisMarkdownService],
    exports: [MarkdownEditorComponent, HtmlForMarkdownPipe, HtmlForGuidedTourMarkdownPipe],
})
export class ArtemisMarkdownModule {}
