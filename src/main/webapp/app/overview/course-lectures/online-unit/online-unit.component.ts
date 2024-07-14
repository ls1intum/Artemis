import { Component } from '@angular/core';
import { LectureUnitDirective } from 'app/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { LectureUnitComponent } from 'app/overview/course-lectures/lecture-unit/lecture-unit.component';
import { faUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-online-unit',
    standalone: true,
    imports: [LectureUnitComponent],
    templateUrl: './online-unit.component.html',
})
export class OnlineUnitComponent extends LectureUnitDirective<OnlineUnit> {
    protected readonly faUpRightFromSquare = faUpRightFromSquare;

    handleIsolatedView() {
        this.logEvent();

        if (this.lectureUnit().source) {
            window.open(this.lectureUnit().source!, '_blank');
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }
}
