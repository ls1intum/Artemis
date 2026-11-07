import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { convertTutorialGroupSummaryArrayDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';

/** Management counterpart of the generated student tutorial-group overview API. */
@Service()
export class TutorialGroupManagementService {
    private readonly tutorialGroupApi = inject(TutorialGroupApi);
    private readonly accountService = inject(AccountService);

    getTutorialGroupsForCourse(courseId: number): Observable<TutorialGroup[]> {
        const groups = this.accountService.isAtLeastInstructorInCourseWithId(courseId)
            ? this.tutorialGroupApi.getTutorialGroupsForCourseForInstructor(courseId)
            : this.tutorialGroupApi.getTutorialGroupsForCourseForTutor(courseId);
        return groups.pipe(map(convertTutorialGroupSummaryArrayDatesFromServer));
    }
}
