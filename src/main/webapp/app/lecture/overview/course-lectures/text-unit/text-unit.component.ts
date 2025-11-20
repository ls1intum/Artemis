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

        const newWindow = window.open('', '_blank')!;
        const apply = () => {
            const document = newWindow.document;

            // title
            document.title = this.lectureUnit().name ?? '';

            // stylesheet
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'public/content/github-markdown.css';
            document.head.appendChild(link);

            // body + content
            document.body.className = 'markdown-body';
            document.body.innerHTML = htmlForMarkdown(this.lectureUnit().content ?? '', []);
            newWindow.focus();
        };

        // ensure DOM is ready in the new window
        const document = newWindow.document;
        if (document.readyState === 'complete' || document.readyState === 'interactive') {
            apply();
        } else {
            document.addEventListener('DOMContentLoaded', apply, { once: true });
        }
    }
}
