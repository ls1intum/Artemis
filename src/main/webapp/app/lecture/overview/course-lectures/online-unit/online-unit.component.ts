import { Component, inject } from '@angular/core';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { faUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { ScienceService } from 'app/foundation/science/science.service';
import { ScienceEventType } from 'app/foundation/science/science.model';

@Component({
    selector: 'jhi-online-unit',
    imports: [LectureUnitComponent],
    templateUrl: './online-unit.component.html',
})
export class OnlineUnitComponent extends LectureUnitDirective<OnlineUnit> {
    private readonly scienceService = inject(ScienceService);

    protected readonly faUpRightFromSquare = faUpRightFromSquare;

    handleIsolatedView() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        const source = this.lectureUnit().source;
        if (source && OnlineUnitComponent.isWebAddress(source)) {
            window.open(source, '_blank');
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    /**
     * Only addresses of web pages are opened, so that a source with another scheme, for example a script, is never opened.
     */
    private static isWebAddress(source: string): boolean {
        try {
            const protocol = new URL(source).protocol;
            return protocol === 'http:' || protocol === 'https:';
        } catch {
            return false;
        }
    }
}
