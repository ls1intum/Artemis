import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { FAQService } from 'app/faq/faq.service';
import { FAQ } from 'app/entities/faq.model';

@Injectable({ providedIn: 'root' })
export class FAQResolve implements Resolve<FAQ> {
    constructor(private faqService: FAQService) {}

    resolve(route: ActivatedRouteSnapshot): Observable<FAQ> {
        const faqId = route.params['faqId'];
        const courseId = route.params['courseId'];
        if (faqId) {
            return this.faqService.find(courseId, faqId).pipe(
                filter((response: HttpResponse<FAQ>) => response.ok),
                map((faq: HttpResponse<FAQ>) => faq.body!),
            );
        }
        return of(new FAQ());
    }
}
