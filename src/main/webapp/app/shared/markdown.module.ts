import { NgModule } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@NgModule({
    imports: [HtmlForMarkdownPipe],
    providers: [ArtemisMarkdownService],
    exports: [HtmlForMarkdownPipe],
})
export class ArtemisMarkdownModule {}
