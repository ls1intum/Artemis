import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { FaqState } from 'app/communication/shared/entities/faq.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Observable, catchError, map, of } from 'rxjs';

export function loadCourseFaqCategories(courseId: number | undefined, alertService: AlertService, faqService: FaqService, faqState?: FaqState): Observable<FaqCategory[]> {
    if (courseId === undefined) {
        return of([]);
    }

    if (faqState) {
        return faqService.findAllCategoriesByCourseIdAndCategory(courseId, faqState).pipe(
            map((categoryRes: HttpResponse<string[]>) => {
                return faqService.convertFaqCategoriesAsStringFromServer(categoryRes.body || []);
            }),
            catchError((error: HttpErrorResponse) => {
                onError(alertService, error);
                return of([]);
            }),
        );
    }

    return faqService.findAllCategoriesByCourseId(courseId).pipe(
        map((categoryRes: HttpResponse<string[]>) => {
            return faqService.convertFaqCategoriesAsStringFromServer(categoryRes.body || []);
        }),
        catchError((error: HttpErrorResponse) => {
            onError(alertService, error);
            return of([]);
        }),
    );
}
