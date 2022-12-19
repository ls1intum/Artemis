import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { EntityArrayResponseType, EntityResponseType } from 'app/exercises/shared/exercise/exercise.service';
import { convertDateFromClient } from 'app/utils/date.utils';

export class MockExerciseService {
    find(exerciseId: number) {
        return MockExerciseService.response({ id: exerciseId } as Exercise);
    }

    getUpcomingExercises() {
        return MockExerciseService.response([{ id: 1 } as Exercise, { id: 2 } as Exercise]);
    }

    // helper method
    private static response<T>(entity: T) {
        return of({ body: entity }) as Observable<HttpResponse<T>>;
    }

    static convertExerciseFromClient<E extends Exercise>(exercise: E): Exercise {
        return exercise;
    }

    static convertExerciseArrayDatesFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        return res;
    }

    static convertExerciseCategoryArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        return res;
    }

    static convertExerciseResponseDatesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        return res;
    }

    static convertExerciseDatesFromClient<E extends Exercise>(exercise: E): E {
        return Object.assign({}, exercise, {
            releaseDate: convertDateFromClient(exercise.releaseDate),
            dueDate: convertDateFromClient(exercise.dueDate),
            assessmentDueDate: convertDateFromClient(exercise.assessmentDueDate),
        });
    }

    static convertExerciseCategoriesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        return res;
    }

    setBonusPointsConstrainedByIncludedInOverallScore(exercise: Exercise) {
        return exercise;
    }

    checkPermission<ERT extends EntityResponseType>(res: ERT): ERT {
        return res;
    }

    static stringifyExerciseCategories(exercise: Exercise) {
        return exercise;
    }

    processExerciseEntityResponse(exerciseRes: EntityResponseType): EntityResponseType {
        return exerciseRes;
    }

    processExerciseEntityArrayResponse(exerciseResArray: EntityArrayResponseType): EntityArrayResponseType {
        return exerciseResArray;
    }
}
