import { Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-text-unit',
    templateUrl: './text-unit.component.html',
    styleUrls: ['./text-unit.component.scss'],
})
export class TextUnitComponent implements OnInit {
    @Input()
    textUnit: TextUnit;
    isCollapsed = true;

    formattedContent?: SafeHtml;

    constructor(private artemisMarkdown: ArtemisMarkdownService) {}

    ngOnInit(): void {
        if (this.textUnit?.content) {
            this.formattedContent = this.artemisMarkdown.safeHtmlForMarkdown(this.textUnit.content);
        }
    }

    handleCollapse($event: any) {
        $event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }

    openPopup($event: any) {
        $event.stopPropagation();
        const win = window.open('', '', 'toolbar=no,location=no,directories=no,status=no,menubar=no,scrollbars=yes,resizable=yes,width=780,height=200');
        win!.document.write(`<html><head><title>${this.textUnit.name}</title>`);
        win!.document.write(
            '<link rel="stylesheet" ' +
                'href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/4.0.0/github-markdown.min.css" ' +
                'integrity="sha512-Oy18vBnbSJkXTndr2n6lDMO5NN31UljR8e/ICzVPrGpSud4Gkckb8yUpqhKuUNoE+o9gAb4O/rAxxw1ojyUVzg==" crossorigin="anonymous" />',
        );
        win!.document.write('</head><body class="markdown-body">');
        win!.document.write('</body></html>');
        win!.document.close();
        win!.document.body.innerHTML = this.artemisMarkdown.htmlForMarkdown(this.textUnit.content, []);
        win!.focus();
    }
}
