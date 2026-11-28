import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RatingListComponent } from 'app/assessment/manage/rating/rating-list/rating-list.component';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { lastValueFrom, of } from 'rxjs';
import type { RatingListItem } from 'app/openapi/model/rating-list-item';
import { TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { provideTranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { TumAetUiPaginatorComponent } from '@tumaet/ui-angular/paginator';

describe('RatingListComponent', () => {
    let component: RatingListComponent;
    let fixture: ComponentFixture<RatingListComponent>;
    let router: Router;
    let http: HttpTestingController;

    const mockRatings: RatingListItem[] = [
        {
            id: 1,
            rating: 4,
            assessmentType: 'MANUAL',
            assessorLogin: 'tutor1',
            assessorName: 'Tutor One',
            resultId: 1001,
            submissionId: 2001,
            participationId: 10,
            exerciseId: 100,
            exerciseTitle: 'Text Exercise 1',
            exerciseType: 'text',
        },
        {
            id: 2,
            rating: 5,
            assessmentType: 'SEMI_AUTOMATIC',
            assessorLogin: 'tutor2',
            assessorName: 'Tutor Two',
            resultId: 1002,
            submissionId: 2002,
            participationId: 20,
            exerciseId: 200,
            exerciseTitle: 'Programming Exercise 1',
            exerciseType: 'programming',
        },
        {
            id: 3,
            rating: 3,
            assessmentType: 'AUTOMATIC',
            assessorLogin: undefined,
            assessorName: undefined,
            resultId: 1003,
            submissionId: 2003,
            participationId: 30,
            exerciseId: 300,
            exerciseTitle: 'Modeling Exercise 1',
            exerciseType: 'modeling',
        },
    ];

    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RatingListComponent, TranslatePipeMock, MockComponent(StarRatingComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideTranslateService(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RatingListComponent);
                component = fixture.componentInstance;
                router = TestBed.inject(Router);
                http = TestBed.inject(HttpTestingController);

                component.ngOnInit();
                respond(mockRatings, 3);
            });
    });

    afterEach(() => {
        http.verify();
    });

    /** Answers the pending rating list request, with the total the server sends in `X-Total-Count`, and returns the query of the request. */
    function respond(body: RatingListItem[], totalCount: number) {
        const request = http.expectOne((req) => req.url.startsWith('/api/assessment/courses/123/rating'));
        expect(request.request.method).toBe('GET');
        request.flush(body, { headers: { 'X-Total-Count': String(totalCount) } });
        return new URL(request.request.url, 'http://localhost').searchParams;
    }

    it('should initialize ratings from paginated response', () => {
        expect(component.ratings()).toHaveLength(3);
        expect(component.totalElements()).toBe(3);
        expect(component.page()).toBe(1);
    });

    it('should request the first page sorted by id with the paging parameters of the dashboard', () => {
        component.loadRatings();

        const query = respond(mockRatings, 3);
        expect(query.get('page')).toBe('0');
        expect(query.get('size')).toBe('20');
        expect(query.getAll('sort')).toEqual(['id,desc']);
    });

    it('should show the total count of the response header as the number of elements', () => {
        component.loadRatings();
        respond(mockRatings, 57);

        expect(component.ratings()).toEqual(mockRatings);
        expect(component.totalElements()).toBe(57);
    });

    it('should load ratings with pagination parameters', () => {
        component.page.set(2);
        component.loadRatings();

        // page is 1-indexed in component, 0-indexed in API
        expect(respond([], 0).get('page')).toBe('1');
    });

    it('should reload ratings on page change', () => {
        component.onPageChange(1);

        expect(component.page()).toBe(2);
        expect(respond([], 0).get('page')).toBe('1');
    });

    it('should reload ratings on sort change', () => {
        component.sortRows();

        expect(respond(mockRatings, 3).getAll('sort')).toEqual(['id,desc']);
    });

    it('should store the sort event, reverse the direction flag and reload with the matching sort parameter', () => {
        component['onSortChange']({ field: 'exerciseTitle', order: 1 });

        expect(component.ratingsSortingPredicate).toBe('exerciseTitle');
        expect(component.ratingsReverseOrder).toBe(true);
        expect(respond(mockRatings, 3).getAll('sort')).toEqual(['exerciseTitle,asc']);

        component['onSortChange']({ field: 'rating', order: -1 });

        expect(component.ratingsSortingPredicate).toBe('rating');
        expect(component.ratingsReverseOrder).toBe(false);
        expect(respond(mockRatings, 3).getAll('sort')).toEqual(['rating,desc']);
    });

    describe('template', () => {
        const element = () => fixture.nativeElement as HTMLElement;

        /** Renders the component; its first change detection runs ngOnInit again, which requests the list once more. */
        function renderLoaded(body: RatingListItem[] = mockRatings, totalCount = 3) {
            fixture.detectChanges();
            respond(body, totalCount);
            fixture.detectChanges();
        }

        it('should render a row per rating and the footer paginator', () => {
            renderLoaded();

            expect(element().querySelectorAll('tbody tr')).toHaveLength(3);
            const paginator = fixture.debugElement.query(By.directive(TumAetUiPaginatorComponent));
            expect(paginator).not.toBeNull();
            expect(paginator.componentInstance.totalRecords()).toBe(3);
        });

        it('should load the next page with a one-based page number when the paginator emits a zero-based page', () => {
            renderLoaded();

            fixture.debugElement.query(By.directive(TumAetUiPaginatorComponent)).componentInstance.pageChange.emit(2);

            expect(component.page()).toBe(3);
            expect(respond([], 0).get('page')).toBe('2');
        });

        it('should reload the sorted ratings when a sortable header is clicked', () => {
            renderLoaded();

            const header = element().querySelector('th[tumAetUiSortableColumn="exerciseTitle"]')!;
            ((header.querySelector('button') ?? header) as HTMLElement).click();

            expect(component.ratingsSortingPredicate).toBe('exerciseTitle');
            expect(respond(mockRatings, 3).getAll('sort')).toEqual(['exerciseTitle,asc']);
        });

        it('should show the empty state and no rows when there are no ratings', () => {
            renderLoaded([], 0);

            expect(element().querySelector('[data-testid="table-empty"]')).not.toBeNull();
            expect(element().querySelectorAll('tbody tr')).toHaveLength(0);
        });
    });

    it('should navigate to assessment view for text exercise', () => {
        const rating = mockRatings[0]; // TEXT exercise
        const routerNavigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));

        component.openResult(rating);

        expect(routerNavigateSpy).toHaveBeenCalledWith(['/course-management', 123, 'text-exercises', 100, 'submissions', 2001, 'assessments', 1001]);
    });

    it('should navigate to assessment view for programming exercise', () => {
        const rating = mockRatings[1]; // PROGRAMMING exercise
        const routerNavigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));

        component.openResult(rating);

        // Programming exercises use a different route (no resultId)
        expect(routerNavigateSpy).toHaveBeenCalledWith(['/course-management', 123, 'programming-exercises', 200, 'submissions', 2002, 'assessment']);
    });

    it('should navigate to assessment view for modeling exercise', () => {
        const rating = mockRatings[2]; // MODELING exercise
        const routerNavigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));

        component.openResult(rating);

        expect(routerNavigateSpy).toHaveBeenCalledWith(['/course-management', 123, 'modeling-exercises', 300, 'submissions', 2003, 'assessments', 1003]);
    });

    it('should return correct assessment type translation key', () => {
        expect(component.getAssessmentTypeTranslationKey('MANUAL')).toBe('artemisApp.AssessmentType.MANUAL');
        expect(component.getAssessmentTypeTranslationKey('AUTOMATIC')).toBe('artemisApp.AssessmentType.AUTOMATIC');
        expect(component.getAssessmentTypeTranslationKey(undefined)).toBe('artemisApp.AssessmentType.null');
    });

    it('should return correct exercise icon', () => {
        const icon = component.getExerciseIcon('text');
        expect(icon).toBeDefined();
    });

    it('should return correct exercise tooltip', () => {
        const tooltip = component.getExerciseTypeTooltip('text');
        expect(tooltip).toBe('artemisApp.exercise.isText');
    });
});
