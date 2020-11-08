import { Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-text-unit',
    templateUrl: './text-unit.component.html',
    styleUrls: ['../../course-exercises/course-exercise-row.scss'],
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

    handleUnitClick($event: any) {
        $event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }
}
