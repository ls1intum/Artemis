import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { effect, inject, input, output } from '@angular/core';
import { Directive } from '@angular/core';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';

@Directive({
    standalone: true,
})
export class LectureUnitDirective<T extends LectureUnit> extends AbstractScienceComponent {
    lectureUnit = input.required<T>();

    readonly onCompletion = output<LectureUnitCompletionEvent>();
    readonly onCollapse = output<boolean>();

    isPresentationMode = input<boolean>(false);

    constructor() {
        const scienceService = inject(ScienceService);

        super(scienceService, ScienceEventType.LECTURE__OPEN_UNIT);

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
