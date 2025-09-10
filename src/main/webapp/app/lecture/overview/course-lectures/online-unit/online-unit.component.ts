import { Component, inject } from '@angular/core';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { faUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

@Component({
    selector: 'jhi-online-unit',
    imports: [LectureUnitComponent],
    templateUrl: './online-unit.component.html',
})
export class OnlineUnitComponent extends LectureUnitDirective<OnlineUnit> {
    protected readonly faUpRightFromSquare = faUpRightFromSquare;

    private readonly scienceService = inject(ScienceService);

    handleIsolatedView() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        if (this.lectureUnit().source) {
            window.open(this.lectureUnit().source!, '_blank');
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }
}
