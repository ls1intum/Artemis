import { Service, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map } from 'rxjs/operators';
import { CreateFaqDTO, Faq, FaqImportCandidate, FaqImportSearchResult, FaqState, UpdateFaqDTO } from 'app/communication/shared/entities/faq.model';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { parseJson } from 'app/foundation/util/json.util';
import { FaqApi } from 'app/openapi/api/faq-api';
import { FaqImportApi } from 'app/openapi/api/faq-import-api';
import { CreateFaq } from 'app/openapi/model/create-faq';
import { FaqFaqStateEnum, Faq as FaqResponse } from 'app/openapi/model/faq';
import { FaqImportCandidate as FaqImportCandidateResponse } from 'app/openapi/model/faq-import-candidate';
import { FaqImportSearchResult as FaqImportSearchResultResponse } from 'app/openapi/model/faq-import-search-result';
import { UpdateFaq } from 'app/openapi/model/update-faq';

type EntityResponseType = HttpResponse<Faq>;
type EntityArrayResponseType = HttpResponse<Faq[]>;

/** Maps the states of the generated client to the client model and back; both use the same strings. */
const FAQ_STATE_FROM_SERVER: Record<FaqFaqStateEnum, FaqState> = {
    ACCEPTED: FaqState.ACCEPTED,
    REJECTED: FaqState.REJECTED,
    PROPOSED: FaqState.PROPOSED,
};

const FAQ_STATE_TO_SERVER: Record<FaqState, FaqFaqStateEnum> = {
    [FaqState.ACCEPTED]: 'ACCEPTED',
    [FaqState.REJECTED]: 'REJECTED',
    [FaqState.PROPOSED]: 'PROPOSED',
};

/**
 * Reads, writes and imports the FAQs of a course through the generated client. It keeps the client model: the server sends each category as a
 * JSON string, which this service decodes into {@link FaqCategory} objects, and full responses keep their status and headers.
 */
@Service()
export class FaqService {
    private readonly faqApi = inject(FaqApi);
    private readonly faqImportApi = inject(FaqImportApi);

    create(courseId: number, createFaqDTO: CreateFaqDTO): Observable<EntityResponseType> {
        const courseIdOfFaq = createFaqDTO.courseId;
        if (courseIdOfFaq === undefined) {
            // The server rejects a FAQ without its course; an editor always sets the course it was opened for.
            return throwError(() => new Error('The course should be present to create FAQ'));
        }
        return this.faqApi.createFaq(courseId, FaqService.convertCreateFaqFromClient(createFaqDTO, courseIdOfFaq)).pipe(map((res) => FaqService.convertFaqResponseFromServer(res)));
    }

    update(courseId: number, updateFaqDTO: UpdateFaqDTO): Observable<EntityResponseType> {
        return this.faqApi
            .updateFaq(courseId, updateFaqDTO.id, FaqService.convertUpdateFaqFromClient(updateFaqDTO))
            .pipe(map((res) => FaqService.convertFaqResponseFromServer(res)));
    }

    find(courseId: number, faqId: number): Observable<EntityResponseType> {
        return this.faqApi.getFaq(courseId, faqId).pipe(map((res) => FaqService.convertFaqResponseFromServer(res)));
    }

    findForManagement(courseId: number, faqId: number): Observable<EntityResponseType> {
        return this.faqApi.getFaqForManagement(courseId, faqId).pipe(map((res) => FaqService.convertFaqResponseFromServer(res)));
    }

    findAllForManagement(courseId: number, faqState?: FaqState): Observable<EntityArrayResponseType> {
        return this.faqApi
            .getFaqsForManagement(courseId, faqState === undefined ? undefined : FAQ_STATE_TO_SERVER[faqState])
            .pipe(map((res) => FaqService.convertFaqArrayResponseFromServer(res)));
    }

    findAllCategoriesForManagement(courseId: number, faqState?: FaqState): Observable<HttpResponse<string[]>> {
        return this.faqApi.getFaqCategoriesForManagement(courseId, faqState === undefined ? undefined : FAQ_STATE_TO_SERVER[faqState]);
    }

    /**
     * Searches visible FAQs outside the target course without loading every source course.
     * @param courseId target course, excluded from the results
     * @param searchTerm literal substring of the question, answer, or course name
     * @param page zero-based result page
     * @returns source FAQ details and exact pagination totals; the server omits an empty page
     */
    searchForImport(courseId: number, searchTerm: string, page: number): Observable<FaqImportSearchResult> {
        return this.faqImportApi.searchForImport(courseId, searchTerm, page).pipe(map((result) => FaqService.convertImportSearchResultFromServer(result)));
    }

    /**
     * Copies selected FAQs into the target course and decodes their categories for the course list.
     * The server rechecks source access and accepts at most 500 distinct source IDs.
     * @param courseId target course where the caller is an instructor
     * @param faqIds source FAQ IDs selected across searches and pages
     * @returns the created FAQ copies with their new IDs
     */
    importFaqs(courseId: number, faqIds: number[]): Observable<EntityArrayResponseType> {
        return this.faqImportApi.importSelected(courseId, { faqIds }).pipe(map((res) => FaqService.convertFaqArrayResponseFromServer(res)));
    }

    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.faqApi.getFaqsForCourse(courseId).pipe(map((res) => FaqService.convertFaqArrayResponseFromServer(res)));
    }

    findAllByCourseIdAndState(courseId: number, faqState: FaqState): Observable<EntityArrayResponseType> {
        return this.faqApi.getAllFaqsForCourseByStatus(courseId, FAQ_STATE_TO_SERVER[faqState]).pipe(map((res) => FaqService.convertFaqArrayResponseFromServer(res)));
    }

    delete(courseId: number, faqId: number): Observable<HttpResponse<void>> {
        return this.faqApi.deleteFaq(courseId, faqId);
    }

    findAllCategoriesByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return this.faqApi.getFaqCategoriesForCourse(courseId);
    }

    findAllCategoriesByCourseIdAndCategory(courseId: number, faqState: FaqState): Observable<HttpResponse<string[]>> {
        return this.faqApi.getFaqCategoriesForCourseByState(courseId, FAQ_STATE_TO_SERVER[faqState]);
    }

    /**
     * Converts the categories of a FAQ into JSON strings (to send them to the server). Returns undefined if no categories exist.
     * @param faq the faq
     */
    static stringifyFaqCategories(faq: CreateFaqDTO | UpdateFaqDTO): string[] | undefined {
        // Skip already-serialized entries so a second call does not double-encode them.
        return faq.categories?.map((category) => (typeof category === 'string' ? category : JSON.stringify(category)));
    }

    convertFaqCategoriesAsStringFromServer(categories: string[]): FaqCategory[] {
        return categories.map((category) => parseJson<FaqCategory>(category));
    }

    /**
     * Creates the request of the generated client without changing the given FAQ.
     * @param createFaq the FAQ to create
     * @param courseId the course of the FAQ, which the server compares with the course in the path
     */
    static convertCreateFaqFromClient(createFaq: CreateFaqDTO, courseId: number): CreateFaq {
        return {
            courseId,
            questionTitle: createFaq.questionTitle,
            questionAnswer: createFaq.questionAnswer,
            categories: FaqService.stringifyFaqCategories(createFaq),
            faqState: FAQ_STATE_TO_SERVER[createFaq.faqState],
        };
    }

    /**
     * Creates the request of the generated client without changing the given FAQ.
     * @param updateFaq the new content of the FAQ
     */
    static convertUpdateFaqFromClient(updateFaq: UpdateFaqDTO): UpdateFaq {
        return {
            id: updateFaq.id,
            questionTitle: updateFaq.questionTitle,
            questionAnswer: updateFaq.questionAnswer,
            categories: FaqService.stringifyFaqCategories(updateFaq),
            faqState: FAQ_STATE_TO_SERVER[updateFaq.faqState],
        };
    }

    /**
     * Creates a client FAQ from a FAQ sent by the server, decoding its categories.
     * @param response the FAQ sent by the server
     */
    private static convertFaqFromServer(response: FaqResponse): Faq {
        const faq = new Faq();
        faq.id = response.id;
        faq.questionTitle = response.questionTitle;
        faq.questionAnswer = response.questionAnswer;
        faq.faqState = FAQ_STATE_FROM_SERVER[response.faqState];
        faq.categories = response.categories?.map((category) => FaqService.parseFaqCategory(category));
        return faq;
    }

    private static convertFaqResponseFromServer(res: HttpResponse<FaqResponse>): EntityResponseType {
        return res.clone({ body: res.body ? FaqService.convertFaqFromServer(res.body) : null });
    }

    private static convertFaqArrayResponseFromServer(res: HttpResponse<FaqResponse[]>): EntityArrayResponseType {
        return res.clone({ body: res.body ? res.body.map((faq) => FaqService.convertFaqFromServer(faq)) : null });
    }

    /**
     * Parses a category sent by the server as JSON string into a {@link FaqCategory}.
     * @param category the JSON string of the category
     */
    private static parseFaqCategory(category: string): FaqCategory {
        const categoryObj = parseJson<FaqCategory>(category);
        return new FaqCategory(categoryObj.category, categoryObj.color);
    }

    private static convertImportSearchResultFromServer(result: FaqImportSearchResultResponse): FaqImportSearchResult {
        return {
            resultsOnPage: (result.resultsOnPage ?? []).map((candidate) => FaqService.convertImportCandidateFromServer(candidate)),
            numberOfPages: result.numberOfPages,
            totalElements: result.totalElements,
        };
    }

    private static convertImportCandidateFromServer(candidate: FaqImportCandidateResponse): FaqImportCandidate {
        return {
            id: candidate.id,
            questionTitle: candidate.questionTitle,
            questionAnswer: candidate.questionAnswer,
            faqState: FAQ_STATE_FROM_SERVER[candidate.faqState],
            courseId: candidate.courseId,
            courseTitle: candidate.courseTitle,
            courseSemester: candidate.courseSemester,
        };
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
        return false;
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
