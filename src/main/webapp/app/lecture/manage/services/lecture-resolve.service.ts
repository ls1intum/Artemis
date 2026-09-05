import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, filter, map, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class LectureResolve implements Resolve<Lecture> {
    private lectureService = inject(LectureService);

    resolve(route: ActivatedRouteSnapshot): Observable<Lecture> {
        const lectureId = route.params['lectureId'];
        if (lectureId) {
            return this.lectureService.find(lectureId).pipe(
                filter((response: HttpResponse<Lecture>) => response.ok),
                map((lecture: HttpResponse<Lecture>) => lecture.body!),
            );
        }
        return of(new Lecture());
    }
}
