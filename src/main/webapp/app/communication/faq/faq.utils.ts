import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { FaqState } from 'app/communication/shared/entities/faq.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { Observable, catchError, map, of } from 'rxjs';

export function loadCourseFaqCategories(
    courseId: number | undefined,
    alertService: AlertService,
    faqService: FaqService,
    faqState?: FaqState,
    audience: 'student' | 'management' = 'student',
): Observable<FaqCategory[]> {
    if (courseId === undefined) {
        return of([]);
    }
    const request =
        audience === 'management'
            ? faqService.findAllCategoriesForManagement(courseId, faqState)
            : faqState
              ? faqService.findAllCategoriesByCourseIdAndCategory(courseId, faqState)
              : faqService.findAllCategoriesByCourseId(courseId);
    return request.pipe(
        map((categoryRes: HttpResponse<string[]>) => faqService.convertFaqCategoriesAsStringFromServer(categoryRes.body || [])),
        catchError((error: HttpErrorResponse) => {
            onError(alertService, error);
            return of([]);
        }),
    );
}
