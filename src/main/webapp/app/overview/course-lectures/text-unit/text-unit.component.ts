import { Component, computed, inject } from '@angular/core';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { LectureUnitComponent } from 'app/overview/course-lectures/lecture-unit/lecture-unit.component';
import { faScroll } from '@fortawesome/free-solid-svg-icons';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { LectureUnitDirective } from 'app/overview/course-lectures/lecture-unit/lecture-unit.directive';

@Component({
    selector: 'jhi-text-unit',
    standalone: true,
    imports: [LectureUnitComponent],
    templateUrl: './text-unit.component.html',
})
export class TextUnitComponent extends LectureUnitDirective<TextUnit> {
    protected readonly faScroll = faScroll;

    private readonly artemisMarkdown = inject(ArtemisMarkdownService);

    readonly formattedContent = computed(() => {
        if (this.lectureUnit().content) {
            return this.artemisMarkdown.safeHtmlForMarkdown(this.lectureUnit().content);
        }
        return undefined;
    });

    handleIsolatedView() {
        // log event
        this.logEvent();

        const win = window.open('about:blank', '_blank')!;
        win.document.write(`<html><head><title>${this.lectureUnit().name}</title>`);
        win.document.write('<link rel="stylesheet" href="public/content/github-markdown.css">');
        win.document.write('</head><body class="markdown-body">');
        win.document.write('</body></html>');
        win.document.close();
        win.document.body.innerHTML = htmlForMarkdown(this.lectureUnit().content, []);
        win.focus();
    }
}
