import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Competency, CourseCompetency } from 'app/entities/competency.model';
import { map, tap } from 'rxjs/operators';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { convertDateFromServer } from 'app/utils/date.utils';

type EntityArrayResponseType = HttpResponse<CourseCompetency[]>;

@Injectable({
    providedIn: 'root',
})
export class CourseCompetencyService {
    private resourceURL = 'api';

    constructor(
        private httpClient: HttpClient,
        private entityTitleService: EntityTitleService,
    ) {}

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<Competency[]>(`${this.resourceURL}/courses/${courseId}/course-competencies`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => CourseCompetencyService.convertArrayResponseDatesFromServer(res)),
            tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
        );
    }

    /**
     * Helper methods for date conversion from server and client
     */
    private static convertArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.map((competency: Competency) => (competency.softDueDate = convertDateFromServer(competency.softDueDate)));
        }
        return res;
    }

    private sendTitlesToEntityTitleService(competency: Competency | undefined | null) {
        this.entityTitleService.setTitle(EntityType.COMPETENCY, [competency?.id], competency?.title);
    }
}
