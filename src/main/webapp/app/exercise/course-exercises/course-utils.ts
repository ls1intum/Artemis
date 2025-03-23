import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { ExerciseService } from 'app/exercise/exercise.service';
import { Observable } from 'rxjs';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

export function loadCourseExerciseCategories(
    courseId: number | undefined,
    courseService: CourseManagementService,
    exerciseService: ExerciseService,
    alertService: AlertService,
): Observable<ExerciseCategory[]> {
    if (courseId === undefined) {
        return new Observable<ExerciseCategory[]>((observer) => {
            observer.complete();
        });
    }

    return new Observable<ExerciseCategory[]>((observer) => {
        courseService.findAllCategoriesOfCourse(courseId).subscribe({
            next: (categoryRes: HttpResponse<string[]>) => {
                const existingCategories = exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                observer.next(existingCategories);
                observer.complete();
            },
            error: (error: HttpErrorResponse) => {
                onError(alertService, error);
                observer.complete();
            },
        });
    });
}
