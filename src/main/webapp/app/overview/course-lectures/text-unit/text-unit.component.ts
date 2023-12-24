import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { faExternalLinkAlt, faScroll } from '@fortawesome/free-solid-svg-icons';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { faSquare, faSquareCheck } from '@fortawesome/free-regular-svg-icons';
import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

@Component({
    selector: 'jhi-text-unit',
    templateUrl: './text-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class TextUnitComponent extends AbstractScienceComponent implements OnInit {
    @Input() textUnit: TextUnit;
    @Input() isPresentationMode = false;
    @Output() onCompletion: EventEmitter<LectureUnitCompletionEvent> = new EventEmitter();

    isCollapsed = true;

    formattedContent?: SafeHtml;

    // Icons
    faExternalLinkAlt = faExternalLinkAlt;
    faScroll = faScroll;
    faSquare = faSquare;
    faSquareCheck = faSquareCheck;

    constructor(
        private artemisMarkdown: ArtemisMarkdownService,
        scienceService: ScienceService,
    ) {
        super(scienceService, ScienceEventType.LECTURE__TEXT_UNIT_OPEN);
    }

    ngOnInit(): void {
        if (this.textUnit?.id) {
            this.setResourceId(this.textUnit.id);
        }
        if (this.textUnit?.content) {
            this.formattedContent = this.artemisMarkdown.safeHtmlForMarkdown(this.textUnit.content);
        }
    }

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;

        if (!this.isCollapsed) {
            // log event
            this.logEvent();

            // Mark the unit as completed when the user opens the unit
            this.onCompletion.emit({ lectureUnit: this.textUnit, completed: true });
        }
    }

    handleClick(event: Event, completed: boolean) {
        event.stopPropagation();
        this.onCompletion.emit({ lectureUnit: this.textUnit, completed });
    }

    openPopup(event: Event) {
        event.stopPropagation();

        // log event
        this.logEvent();

        const win = window.open('about:blank', '_blank')!;
        win.document.write(`<html><head><title>${this.textUnit.name}</title>`);
        win.document.write('<link rel="stylesheet" href="public/content/github-markdown.css">');
        win.document.write('</head><body class="markdown-body">');
        win.document.write('</body></html>');
        win.document.close();
        win.document.body.innerHTML = htmlForMarkdown(this.textUnit.content, []);
        win.focus();
    }
}
