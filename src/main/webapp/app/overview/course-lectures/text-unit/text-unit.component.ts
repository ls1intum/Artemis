import { Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { faExternalLinkAlt, faScroll } from '@fortawesome/free-solid-svg-icons';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';

@Component({
    selector: 'jhi-text-unit',
    templateUrl: './text-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class TextUnitComponent implements OnInit {
    @Input() textUnit: TextUnit;
    isCollapsed = true;

    formattedContent?: SafeHtml;

    // Icons
    faExternalLinkAlt = faExternalLinkAlt;
    faScroll = faScroll;

    constructor(private artemisMarkdown: ArtemisMarkdownService) {}

    ngOnInit(): void {
        if (this.textUnit?.content) {
            this.formattedContent = this.artemisMarkdown.safeHtmlForMarkdown(this.textUnit.content);
        }
    }

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }

    openPopup(event: Event) {
        event.stopPropagation();

        const win = window.open('about:blank', '_blank')!;
        win.document.write(`<html><head><title>${this.textUnit.name}</title>`);
        win.document.write(`<link rel="stylesheet" href="${SERVER_API_URL}public/content/github-markdown.css">`);
        win.document.write('</head><body class="markdown-body">');
        win.document.write('</body></html>');
        win.document.close();
        win.document.body.innerHTML = htmlForMarkdown(this.textUnit.content, []);
        win.focus();
    }
}
