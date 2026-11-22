import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSummary } from 'app/openapi/model/tutorial-group-summary';
import { convertTutorialGroupSummaryArrayDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';

/** Management counterpart of the generated student tutorial-group overview API. */
@Service()
export class TutorialGroupManagementService {
    private readonly http = inject(HttpClient);
    private readonly accountService = inject(AccountService);

    getTutorialGroupsForCourse(courseId: number): Observable<TutorialGroup[]> {
        const audience = this.accountService.isAtLeastInstructorInCourseWithId(courseId) ? 'for-instructor' : 'for-tutor';
        return this.http
            .get<TutorialGroupSummary[]>(`/api/tutorialgroup/courses/${courseId}/tutorial-groups/${audience}`)
            .pipe(map(convertTutorialGroupSummaryArrayDatesFromServer));
    }
}
