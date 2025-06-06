import { Component, computed, inject } from '@angular/core';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { faScroll } from '@fortawesome/free-solid-svg-icons';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

@Component({
    selector: 'jhi-text-unit',
    imports: [LectureUnitComponent],
    templateUrl: './text-unit.component.html',
})
export class TextUnitComponent extends LectureUnitDirective<TextUnit> {
    protected readonly faScroll = faScroll;

    private readonly artemisMarkdown = inject(ArtemisMarkdownService);
    private readonly scienceService = inject(ScienceService);

    readonly formattedContent = computed(() => {
        if (this.lectureUnit().content) {
            return this.artemisMarkdown.safeHtmlForMarkdown(this.lectureUnit().content);
        }
        return undefined;
    });

    handleIsolatedView() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

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
