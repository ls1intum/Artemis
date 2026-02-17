import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { input, output } from '@angular/core';
import { Directive } from '@angular/core';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';

@Directive()
export class LectureUnitDirective<T extends LectureUnit> {
    courseId = input.required<number>();
    lectureUnit = input.required<T>();
    initiallyExpanded = input<boolean>(false);

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
