import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Faq, FaqState } from 'app/entities/faq.model';
import { FaqCategory } from 'app/entities/faq-category.model';

type EntityResponseType = HttpResponse<Faq>;
type EntityArrayResponseType = HttpResponse<Faq[]>;

@Injectable({ providedIn: 'root' })
export class FaqService {
    public resourceUrl = 'api/courses';

    constructor(protected http: HttpClient) {}

    create(courseId: number, faq: Faq): Observable<EntityResponseType> {
        const copy = FaqService.convertFaqFromClient(faq);
        return this.http.post<Faq>(`${this.resourceUrl}/${courseId}/faqs`, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                return res;
            }),
        );
    }

    update(courseId: number, faq: Faq): Observable<EntityResponseType> {
        const copy = FaqService.convertFaqFromClient(faq);
        return this.http.put<Faq>(`${this.resourceUrl}/${courseId}/faqs/${faq.id}`, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                return res;
            }),
        );
    }

    find(courseId: number, faqId: number): Observable<EntityResponseType> {
        return this.http
            .get<Faq>(`${this.resourceUrl}/${courseId}/faqs/${faqId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => FaqService.convertFaqCategoriesFromServer(res)));
    }

    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Faq[]>(`${this.resourceUrl}/${courseId}/faqs`, {
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => FaqService.convertFaqCategoryArrayFromServer(res)));
    }

    findAllByCourseIdAndState(courseId: number, faqState: FaqState): Observable<EntityArrayResponseType> {
        return this.http
            .get<Faq[]>(`${this.resourceUrl}/${courseId}/faq-state/${faqState}`, {
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => FaqService.convertFaqCategoryArrayFromServer(res)));
    }

    delete(courseId: number, faqId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/faqs/${faqId}`, { observe: 'response' });
    }

    findAllCategoriesByCourseId(courseId: number) {
        return this.http.get<string[]>(`${this.resourceUrl}/${courseId}/faq-categories`, {
            observe: 'response',
        });
    }
    /**
     * Converts the faq category json string into FaqCategory objects (if it exists).
     * @param res the response
     */
    static convertFaqCategoriesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body?.categories) {
            FaqService.parseFaqCategories(res.body);
        }
        return res;
    }

    /**
     * Converts a faqs categories into a json string (to send them to the server). Does nothing if no categories exist
     * @param faq the faq
     */
    static stringifyFaqCategories(faq: Faq) {
        return faq.categories?.map((category) => JSON.stringify(category) as unknown as FaqCategory);
    }

    convertFaqCategoriesAsStringFromServer(categories: string[]): FaqCategory[] {
        return categories.map((category) => JSON.parse(category));
    }

    /**
     * Converts the faq category json strings into FaqCategory objects (if it exists).
     * @param res the response
     */
    static convertFaqCategoryArrayFromServer<E extends Faq, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((faq: E) => FaqService.parseFaqCategories(faq));
        }
        return res;
    }

    /**
     * Parses the faq categories JSON string into {@link FaqCategory} objects.
     * @param faq - the faq
     */
    static parseFaqCategories(faq?: Faq) {
        if (faq?.categories) {
            faq.categories = faq.categories.map((category) => {
                const categoryObj = JSON.parse(category as unknown as string);
                return new FaqCategory(categoryObj.category, categoryObj.color);
            });
        }
    }

    /**
     * Prepare client-faq to be uploaded to the server
     * @param { Faq } faq - faq that will be modified
     */
    static convertFaqFromClient<F extends Faq>(faq: F): Faq {
        const copy = Object.assign({}, faq);
        copy.categories = FaqService.stringifyFaqCategories(copy);
        return copy;
    }

    toggleFilter(category: string, activeFilters: Set<string>) {
        if (activeFilters.has(category)) {
            activeFilters.delete(category);
        } else {
            activeFilters.add(category);
        }
        return activeFilters;
    }

    applyFilters(activeFilters: Set<string>, faqs: Faq[]): Faq[] {
        if (activeFilters.size === 0) {
            // If no filters selected, show all faqs
            return faqs;
        } else {
            return faqs.filter((faq) => this.hasFilteredCategory(faq, activeFilters));
        }
    }

    hasFilteredCategory(faq: Faq, filteredCategory: Set<string>) {
        const categories = faq.categories?.map((category) => category.category);
        if (categories) {
            return categories.some((category) => filteredCategory.has(category!));
        }
    }

    hasSearchTokens(faq: Faq, searchTerm: string): boolean {
        if (searchTerm === '') {
            return true;
        }
        const tokens = searchTerm.toLowerCase().split(' ');
        const faqText = `${faq.questionTitle ?? ''} ${faq.questionAnswer ?? ''}`.toLowerCase();
        return tokens.every((token) => faqText.includes(token));
    }
}
