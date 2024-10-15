import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { FaqService } from 'app/faq/faq.service';
import { Faq } from 'app/entities/faq.model';

@Injectable({ providedIn: 'root' })
export class FaqResolve implements Resolve<Faq> {
    constructor(private faqService: FaqService) {}

    resolve(route: ActivatedRouteSnapshot): Observable<Faq> {
        const faqId = route.params['faqId'];
        const courseId = route.params['courseId'];
        if (faqId) {
            return this.faqService.find(courseId, faqId).pipe(
                filter((response: HttpResponse<Faq>) => response.ok),
                map((faq: HttpResponse<Faq>) => faq.body!),
            );
        }
        return of(new Faq());
    }
}
