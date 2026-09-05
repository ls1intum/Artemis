import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { computed, input, output } from '@angular/core';
import { Directive } from '@angular/core';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { LectureDeepLink } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';

@Directive()
export class LectureUnitDirective<T extends LectureUnit> {
    courseId = input.required<number>();
    lectureUnit = input.required<T>();
    readonly deepLink = input<LectureDeepLink | undefined>(undefined);

    readonly matchedDeepLink = computed(() => {
        const deepLink = this.deepLink();
        return deepLink?.unitId === this.lectureUnit()?.id ? deepLink : undefined;
    });

    readonly onCompletion = output<LectureUnitCompletionEvent>();
    readonly onCollapse = output<boolean>();

    isPresentationMode = input<boolean>(false);

    toggleCompletion(completed: boolean) {
        this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: completed });
    }

    toggleCollapse(isCollapsed: boolean) {
        this.onCollapse.emit(isCollapsed);
    }
}
