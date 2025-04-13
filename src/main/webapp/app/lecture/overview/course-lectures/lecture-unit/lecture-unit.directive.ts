import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ScienceEventType } from 'app/shared/science/science.model';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { effect, input, output } from '@angular/core';
import { Directive } from '@angular/core';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';

@Directive()
export class LectureUnitDirective<T extends LectureUnit> extends AbstractScienceComponent {
    lectureUnit = input.required<T>();

    readonly onCompletion = output<LectureUnitCompletionEvent>();
    readonly onCollapse = output<boolean>();

    isPresentationMode = input<boolean>(false);

    constructor() {
        super(ScienceEventType.LECTURE__OPEN_UNIT);

        effect(() => {
            this.setResourceId(this.lectureUnit().id!);
        });
    }

    toggleCompletion(completed: boolean) {
        this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: completed });
    }

    toggleCollapse(isCollapsed: boolean) {
        this.onCollapse.emit(isCollapsed);
    }
}
