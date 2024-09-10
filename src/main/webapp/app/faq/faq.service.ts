import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Faq, FaqState } from 'app/entities/faq.model'
import { Exercise } from 'app/entities/exercise.model';
import { FaqCategory } from 'app/entities/faq-category.model';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

type EntityResponseType = HttpResponse<Faq>;
type EntityArrayResponseType = HttpResponse<Faq[]>;


@Injectable({ providedIn: 'root' })
export class FaqService {

    public resourceUrl = 'api/courses';

    constructor(
        protected http: HttpClient,
        protected alertService: AlertService

    ) {}

    create(faq: Faq): Observable<EntityResponseType>{
        let copy = FaqService.convertFaqFromClient(faq)
        faq.faqState = FaqState.ACCEPTED
        return this.http.post<Faq>( `api/faqs`,copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                return res;
            }),
        );

    }

    update(faq: Faq): Observable<EntityResponseType>{
        let copy = FaqService.convertFaqFromClient(faq)
        return this.http.put<Faq>(`api/faqs/${faq.id}`, copy, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                return res;
            }),
        );

    }

    find(faqId: number): Observable<EntityResponseType> {
        return this.http.get<Faq>(`api/faqs/${faqId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) =>
                 FaqService.convertFaqCategoriesFromServer(res)
            ),
        );
    }


    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Faq[]>(this.resourceUrl+`/${courseId}/faqs`, {
                observe: 'response',
            })
            .pipe(
                map((res: EntityArrayResponseType) => FaqService.convertExerciseCategoryArrayFromServer(res))
            );
    }

    delete(faqId: number): Observable<HttpResponse<void>>{
        return this.http.delete<void>(`api/faqs/${faqId}`, { observe: 'response' })
    }

    findAllCategoriesByCourseId(courseId: number) {
        return this.http.get<String[]>(this.resourceUrl+`/${courseId}/faq-categories`, {
            observe: 'response',
        })
    }
    /**
     * Converts the faq category json string into FaqCategory objects (if it exists).
     * @param res the response
     */
    static convertFaqCategoriesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body && res.body.categories) {
            FaqService.parseExerciseCategories(res.body);
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

    convertFaqCategoriesAsStringFromServer(categories: string[]): ExerciseCategory[] {
        return categories.map((category) => JSON.parse(category));
    }

    /**
     * Converts the faq category json strings into FaqCategory objects (if it exists).
     * @param res the response
     */
    static convertExerciseCategoryArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((exercise: E) => FaqService.parseExerciseCategories(exercise));
        }
        return res;
    }

    /**
     * Parses the faq categories JSON string into {@link FaqCategory} objects.
     * @param faq - the exercise
     */
    static parseExerciseCategories(faq?: Faq) {
        if (faq?.categories) {
            faq.categories = faq.categories.map((category) => {
                const categoryObj = JSON.parse(category as unknown as string);
                return new FaqCategory(categoryObj.category, categoryObj.color);
            });
        }
    }

    static parseFaqCategoriesString(categories?: String[]) {
        let faqCategories: FaqCategory[] = []
        if (categories) {
            faqCategories = categories.map((category) => {
                const categoryObj = JSON.parse(category as unknown as string);
                return new FaqCategory(categoryObj.category, categoryObj.color);
            });

        }
        return faqCategories
    }

    /**
     * Prepare client-faq to be uploaded to the server
     * @param { Faq } faq - faq that will be modified
     */
    static convertFaqFromClient<F extends Faq>(faq: F): Faq {
        let copy = Object.assign(faq, {});
        copy.categories = FaqService.stringifyFaqCategories(copy);
        if (copy.categories) {

        }
        return copy;
    }



}
