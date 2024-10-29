import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Observable, catchError, map, of } from 'rxjs';
import { FaqService } from 'app/faq/faq.service';
import { FaqCategory } from 'app/entities/faq-category.model';

export function loadCourseFaqCategories(courseId: number | undefined, alertService: AlertService, faqService: FaqService): Observable<FaqCategory[]> {
    if (courseId === undefined) {
        return of([]);
    }

    return faqService.findAllCategoriesByCourseId(courseId).pipe(
        map((categoryRes: HttpResponse<string[]>) => {
            const existingCategories = faqService.convertFaqCategoriesAsStringFromServer(categoryRes.body || []);
            return existingCategories;
        }),
        catchError((error: HttpErrorResponse) => {
            onError(alertService, error);
            return of([]);
        }),
    );
}
