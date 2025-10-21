import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Injectable, inject } from '@angular/core';

import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { map } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseResolve implements Resolve<ProgrammingExercise> {
    private service = inject(ProgrammingExerciseService);

    resolve(route: ActivatedRouteSnapshot) {
        const exerciseId = route.params['exerciseId'] ? route.params['exerciseId'] : undefined;
        if (exerciseId) {
            return this.service.find(exerciseId, true, true).pipe(map((programmingExercise: HttpResponse<ProgrammingExercise>) => programmingExercise.body!));
        }
        return of(new ProgrammingExercise(undefined, undefined));
    }
}
