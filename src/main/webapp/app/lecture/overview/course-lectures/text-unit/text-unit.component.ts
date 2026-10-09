import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { faExpand, faScroll } from '@fortawesome/free-solid-svg-icons';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';

@Component({
    selector: 'jhi-text-unit',
    imports: [LectureUnitComponent],
    templateUrl: './text-unit.component.html',
})
export class TextUnitComponent extends LectureUnitDirective<TextUnit> {
    private readonly artemisMarkdown = inject(ArtemisMarkdownService);
    private readonly router = inject(Router);
    private readonly activatedRoute = inject(ActivatedRoute);

    protected readonly faScroll = faScroll;
    protected readonly faExpand = faExpand;

    readonly formattedContent = computed(() => {
        if (this.lectureUnit().content) {
            return this.artemisMarkdown.safeHtmlForMarkdown(this.lectureUnit().content);
        }
        return undefined;
    });

    /**
     * The lecture the unit belongs to. Units shown outside of the lecture page (competencies, learning path) carry a
     * reference to their lecture, units loaded as part of a lecture do not and use the lecture of the route instead.
     * Without either, the full screen page cannot be addressed and its button is not offered.
     */
    protected readonly lectureId = computed(() => {
        const lectureIdOfUnit = this.lectureUnit().lecture?.id;
        if (lectureIdOfUnit !== undefined) {
            return lectureIdOfUnit;
        }
        const lectureIdOfRoute = this.activatedRoute.snapshot.paramMap.get('lectureId');
        return lectureIdOfRoute ? Number(lectureIdOfRoute) : undefined;
    });

    /** Opens the text on its own page inside Artemis, where only the main navbar and the footer remain around it. */
    handleIsolatedView() {
        const lectureId = this.lectureId();
        if (lectureId === undefined) {
            return;
        }
        void this.router.navigate(['/courses', this.courseId(), 'lectures', lectureId, 'text-units', this.lectureUnit().id]);
    }
}
