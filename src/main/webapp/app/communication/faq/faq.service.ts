import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CreateFaqDTO, Faq, FaqState, UpdateFaqDTO } from 'app/communication/shared/entities/faq.model';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';

type EntityResponseType = HttpResponse<Faq>;
type EntityArrayResponseType = HttpResponse<Faq[]>;

@Injectable({ providedIn: 'root' })
export class FaqService {
    public resourceUrl = 'api/communication/courses';

    private http = inject(HttpClient);

    create(courseId: number, createFaqDTO: CreateFaqDTO): Observable<EntityResponseType> {
        const copy = FaqService.convertCreateFaqFromClient(createFaqDTO);
        return this.http.post<Faq>(`${this.resourceUrl}/${courseId}/faqs`, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                return res;
            }),
        );
    }

    update(courseId: number, updateFaqDTO: UpdateFaqDTO): Observable<EntityResponseType> {
        const copy = FaqService.convertUpdateFaqFromClient(updateFaqDTO);
        return this.http.put<Faq>(`${this.resourceUrl}/${courseId}/faqs/${updateFaqDTO.id}`, copy, { observe: 'response' }).pipe(
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
    static stringifyFaqCategories(faq: CreateFaqDTO | UpdateFaqDTO) {
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
     * @param { CreateFaqDTO } createFaq - faq that will be modified
     */
    static convertCreateFaqFromClient<F extends CreateFaqDTO>(createFaq: F): CreateFaqDTO {
        const copy = Object.assign({}, createFaq);
        copy.categories = FaqService.stringifyFaqCategories(copy);
        return copy;
    }

    /**
     * Prepare client-faq to be uploaded to the server
     * @param { UpdateFaqDTO } updateFaq - faq that will be modified
     */
    static convertUpdateFaqFromClient<F extends UpdateFaqDTO>(updateFaq: F): UpdateFaqDTO {
        const copy = Object.assign({}, updateFaq);
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

    findAllCategoriesByCourseIdAndCategory(courseId: number, faqState: FaqState) {
        return this.http.get<string[]>(`${this.resourceUrl}/${courseId}/faq-categories/${faqState}`, {
            observe: 'response',
        });
    }

    /**
     * Trigger the Ingestion of all Faqs in the course.
     */
    ingestFaqsInPyris(courseId: number): Observable<HttpResponse<void>> {
        const params = new HttpParams();
        return this.http.post<void>(`api/communication/courses/${courseId}/faqs/ingest`, null, {
            params: params,
            observe: 'response',
        });
    }
    enable(courseId: number): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/${courseId}/faqs/enable`, null, { observe: 'response' });
    }
}
