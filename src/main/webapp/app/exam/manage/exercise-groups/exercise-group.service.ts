import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { toExerciseGroup } from 'app/exam/manage/util/exercise-group.util';
import { ExerciseGroupApi } from 'app/openapi/api/exercise-group-api';

@Service()
export class ExerciseGroupService {
    private exerciseGroupApi = inject(ExerciseGroupApi);

    /**
     * Find an exercise group on the server and convert it into an {@link ExerciseGroup}.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseGroupId The id of the exercise group to get.
     */
    find(courseId: number, examId: number, exerciseGroupId: number): Observable<ExerciseGroup> {
        return this.exerciseGroupApi.getExerciseGroup(courseId, examId, exerciseGroupId).pipe(map(toExerciseGroup));
    }
}
