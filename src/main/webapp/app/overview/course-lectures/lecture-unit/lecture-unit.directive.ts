import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { effect, input, output } from '@angular/core';
import { Directive } from '@angular/core';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';

@Directive({
    standalone: true,
})
export class LectureUnitDirective<T extends LectureUnit> extends AbstractScienceComponent {
    readonly lectureUnit = input.required<T>();

    readonly onCompletion = output<LectureUnitCompletionEvent>();
    readonly onCollapse = output<boolean>();

    readonly isPresentationMode = input<boolean>(false);

    constructor(scienceService: ScienceService) {
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
