import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Observable, catchError, map, of } from 'rxjs';
import { FAQService } from 'app/faq/faq.service';
import { FAQCategory } from 'app/entities/faq-category.model';

export function loadCourseFaqCategories(courseId: number | undefined, alertService: AlertService, faqService: FAQService): Observable<FAQCategory[]> {
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
