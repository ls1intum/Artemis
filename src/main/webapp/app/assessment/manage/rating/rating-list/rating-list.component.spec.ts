import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { RatingListComponent } from 'app/assessment/manage/rating/rating-list/rating-list.component';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { ActivatedRoute, Router } from '@angular/router';
import { lastValueFrom, of } from 'rxjs';
import { RatingListItem } from 'app/assessment/shared/entities/rating-list-item.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { PageableResult } from 'app/shared/table/pageable-table';

describe('RatingListComponent', () => {
    setupTestBed({ zoneless: true });
    let component: RatingListComponent;
    let fixture: ComponentFixture<RatingListComponent>;
    let router: Router;
    let ratingService: RatingService;

    const mockRatings: RatingListItem[] = [
        {
            id: 1,
            rating: 4,
            assessmentType: AssessmentType.MANUAL,
            assessorLogin: 'tutor1',
            assessorName: 'Tutor One',
            resultId: 1001,
            submissionId: 2001,
            participationId: 10,
            exerciseId: 100,
            exerciseTitle: 'Text Exercise 1',
            exerciseType: ExerciseType.TEXT,
        },
        {
            id: 2,
            rating: 5,
            assessmentType: AssessmentType.SEMI_AUTOMATIC,
            assessorLogin: 'tutor2',
            assessorName: 'Tutor Two',
            resultId: 1002,
            submissionId: 2002,
            participationId: 20,
            exerciseId: 200,
            exerciseTitle: 'Programming Exercise 1',
            exerciseType: ExerciseType.PROGRAMMING,
        },
        {
            id: 3,
            rating: 3,
            assessmentType: AssessmentType.AUTOMATIC,
            assessorLogin: undefined,
            assessorName: undefined,
            resultId: 1003,
            submissionId: 2003,
            participationId: 30,
            exerciseId: 300,
            exerciseTitle: 'Modeling Exercise 1',
            exerciseType: ExerciseType.MODELING,
        },
    ];

    const mockPageableResult: PageableResult<RatingListItem> = {
        content: mockRatings,
        totalElements: 3,
        totalPages: 1,
    };

    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RatingListComponent, TranslatePipeMock, MockComponent(StarRatingComponent), MockDirective(SortDirective), MockComponent(NgbPagination)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(RatingService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RatingListComponent);
                component = fixture.componentInstance;
                router = TestBed.inject(Router);
                ratingService = TestBed.inject(RatingService);

                vi.spyOn(ratingService, 'getRatingsForDashboard').mockReturnValue(of(mockPageableResult));

                component.ngOnInit();
            });
    });

    it('should initialize ratings from paginated response', () => {
        expect(component.ratings).toHaveLength(3);
        expect(component.totalElements).toBe(3);
        expect(component.page).toBe(1);
    });

    it('should load ratings with pagination parameters', () => {
        const serviceSpy = vi.spyOn(ratingService, 'getRatingsForDashboard');
        component.page = 2;
        component.loadRatings();

        // page is 1-indexed in component, 0-indexed in API
        expect(serviceSpy).toHaveBeenCalledWith(123, 1, 20, 'id,desc');
    });

    it('should reload ratings on page change', () => {
        const loadSpy = vi.spyOn(component, 'loadRatings');
        component.onPageChange();
        expect(loadSpy).toHaveBeenCalled();
    });

    it('should reload ratings on sort change', () => {
        const loadSpy = vi.spyOn(component, 'loadRatings');
        component.sortRows();
        expect(loadSpy).toHaveBeenCalled();
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
        expect(component.getAssessmentTypeTranslationKey(AssessmentType.MANUAL)).toBe('artemisApp.AssessmentType.MANUAL');
        expect(component.getAssessmentTypeTranslationKey(AssessmentType.AUTOMATIC)).toBe('artemisApp.AssessmentType.AUTOMATIC');
        expect(component.getAssessmentTypeTranslationKey(undefined)).toBe('artemisApp.AssessmentType.null');
    });

    it('should return correct exercise icon', () => {
        const icon = component.getExerciseIcon(ExerciseType.TEXT);
        expect(icon).toBeDefined();
    });

    it('should return correct exercise tooltip', () => {
        const tooltip = component.getExerciseTypeTooltip(ExerciseType.TEXT);
        expect(tooltip).toBe('artemisApp.exercise.isText');
    });
});
