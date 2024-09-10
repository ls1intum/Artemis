import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Observable } from 'rxjs';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { FaqService } from 'app/faq/faq.service';
import { FaqCategory } from 'app/entities/faq-category.model';

export function loadCourseFaqCategories(
    courseId: number | undefined,
    alertService: AlertService,
    faqService: FaqService
): Observable<FaqCategory[]> {
    if (courseId === undefined) {
        return new Observable<ExerciseCategory[]>((observer) => {
            observer.complete();
        });
    }

    return new Observable<ExerciseCategory[]>((observer) => {
        faqService.findAllCategoriesByCourseId(courseId).subscribe({
            next: (categoryRes: HttpResponse<string[]>) => {
                const existingCategories = faqService.convertFaqCategoriesAsStringFromServer(categoryRes.body!);
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
