import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Faq, FaqState } from 'app/entities/faq.model';
import { FAQCategory } from 'app/entities/faq-category.model';
import { AlertService } from 'app/core/util/alert.service';

type EntityResponseType = HttpResponse<Faq>;
type EntityArrayResponseType = HttpResponse<Faq[]>;

@Injectable({ providedIn: 'root' })
export class FAQService {
    public resourceUrl = 'api/courses';

    constructor(
        protected http: HttpClient,
        protected alertService: AlertService,
    ) {}

    create(faq: Faq): Observable<EntityResponseType> {
        const copy = FAQService.convertFaqFromClient(faq);
        faq.faqState = FaqState.ACCEPTED;
        return this.http.post<Faq>(`api/faqs`, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                return res;
            }),
        );
    }

    update(faq: Faq): Observable<EntityResponseType> {
        const copy = FAQService.convertFaqFromClient(faq);
        return this.http.put<Faq>(`api/faqs/${faq.id}`, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                return res;
            }),
        );
    }

    find(faqId: number): Observable<EntityResponseType> {
        return this.http.get<Faq>(`api/faqs/${faqId}`, { observe: 'response' }).pipe(map((res: EntityResponseType) => FAQService.convertFaqCategoriesFromServer(res)));
    }

    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Faq[]>(this.resourceUrl + `/${courseId}/faqs`, {
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => FAQService.convertFaqCategoryArrayFromServer(res)));
    }

    delete(faqId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`api/faqs/${faqId}`, { observe: 'response' });
    }

    findAllCategoriesByCourseId(courseId: number) {
        return this.http.get<string[]>(this.resourceUrl + `/${courseId}/faq-categories`, {
            observe: 'response',
        });
    }
    /**
     * Converts the faq category json string into FaqCategory objects (if it exists).
     * @param res the response
     */
    static convertFaqCategoriesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body && res.body.categories) {
            FAQService.parseFaqCategories(res.body);
        }
        return res;
    }

    /**
     * Converts a faqs categories into a json string (to send them to the server). Does nothing if no categories exist
     * @param faq the faq
     */
    static stringifyFaqCategories(faq: Faq) {
        return faq.categories?.map((category) => JSON.stringify(category) as unknown as FAQCategory);
    }

    convertFaqCategoriesAsStringFromServer(categories: string[]): FAQCategory[] {
        return categories.map((category) => JSON.parse(category));
    }

    /**
     * Converts the faq category json strings into FaqCategory objects (if it exists).
     * @param res the response
     */
    static convertFaqCategoryArrayFromServer<E extends Faq, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((faq: E) => FAQService.parseFaqCategories(faq));
        }
        return res;
    }

    /**
     * Parses the faq categories JSON string into {@link FAQCategory} objects.
     * @param faq - the faq
     */
    static parseFaqCategories(faq?: Faq) {
        if (faq?.categories) {
            faq.categories = faq.categories.map((category) => {
                const categoryObj = JSON.parse(category as unknown as string);
                return new FAQCategory(categoryObj.category, categoryObj.color);
            });
        }
    }

    /**
     * Prepare client-faq to be uploaded to the server
     * @param { Faq } faq - faq that will be modified
     */
    static convertFaqFromClient<F extends Faq>(faq: F): Faq {
        const copy = Object.assign(faq, {});
        copy.categories = FAQService.stringifyFaqCategories(copy);
        if (copy.categories) {
        }
        return copy;
    }

    static toggleFilter(category: string, activeFilters: Set<string>) {
        if (activeFilters.has(category)) {
            activeFilters.delete(category);
            return activeFilters;
        } else {
            activeFilters.add(category);
            return activeFilters;
        }
    }

    static applyFilters(activeFilters: Set<string>, faqs: Faq[]): Faq[] {
        let filteredFaq: Faq[];
        if (activeFilters.size === 0) {
            // If no filters selected, show all faqs
            filteredFaq = faqs;
        } else {
            filteredFaq = faqs.filter((faq) => this.hasFilteredCategory(faq, activeFilters));
        }
        return filteredFaq;
    }

    public static hasFilteredCategory(faq: Faq, filteredCategory: Set<string>) {
        const categories = faq.categories?.map((category) => category.category);
        if (categories) {
            return categories.some((category) => filteredCategory.has(category!));
        }
    }
}
