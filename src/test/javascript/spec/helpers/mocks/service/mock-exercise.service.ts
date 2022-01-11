import { of, Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { EntityArrayResponseType, EntityResponseType } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';

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

    static convertExerciseForServer<E extends Exercise>(exercise: E): Exercise {
        return exercise;
    }

    static convertDateArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        return res;
    }

    static convertExerciseCategoryArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        return res;
    }

    static convertDateFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        return res;
    }

    static convertDateFromClient<E extends Exercise>(exercise: E): E {
        return Object.assign({}, exercise, {
            releaseDate: exercise.releaseDate && dayjs(exercise.releaseDate).isValid() ? dayjs(exercise.releaseDate).toJSON() : undefined,
            dueDate: exercise.dueDate && dayjs(exercise.dueDate).isValid() ? dayjs(exercise.dueDate).toJSON() : undefined,
            assessmentDueDate: exercise.assessmentDueDate && dayjs(exercise.assessmentDueDate).isValid() ? dayjs(exercise.assessmentDueDate).toJSON() : undefined,
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
