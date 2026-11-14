import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Service, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';

@Service()
export class LectureResolve implements Resolve<Lecture> {
    private lectureService = inject(LectureService);

    resolve(route: ActivatedRouteSnapshot): Observable<Lecture> {
        const lectureId = route.params['lectureId'];
        if (lectureId) {
            return this.lectureService.find(lectureId);
        }
        return of(new Lecture());
    }
}
