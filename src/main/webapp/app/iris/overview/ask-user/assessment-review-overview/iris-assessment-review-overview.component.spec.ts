import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisAssessmentReviewExerciseComponent } from 'app/iris/overview/ask-user/assessment-review-overview/assessment-review-exercise/iris-assessment-review-exercise.component';
import { IrisAssessmentReviewHttpService, IrisAssessmentReviewParticipation } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { FilterProp, IrisAssessmentReviewOverviewComponent } from 'app/iris/overview/ask-user/assessment-review-overview/iris-assessment-review-overview.component';

describe('IrisAssessmentReviewOverviewComponent', () => {
    let fixture: ComponentFixture<IrisAssessmentReviewOverviewComponent>;
    let component: IrisAssessmentReviewOverviewComponent;
    let searchParticipationsStub: ReturnType<typeof vi.fn>;
    let getCourseSettingsStub: ReturnType<typeof vi.fn>;
    let alertService: AlertService;

    const exercise = { id: 11, type: ExerciseType.PROGRAMMING, teamMode: false, title: 'Programming Exercise' };
    const course = { id: 7, exercises: [exercise] } as Course;
    const participation = {
        id: 21,
        exerciseId: 11,
        student: { name: 'Student One', login: 'student1' },
        submissionCount: 2,
    } as IrisAssessmentReviewParticipation;

    beforeEach(async () => {
        searchParticipationsStub = vi.fn(() =>
            of({
                content: [participation],
                totalElements: 1,
                participationsPerFilter: new Map<string, number>([
                    [FilterProp.ALL, 1],
                    [FilterProp.SUSPICIOUS, 1],
                ]),
            }),
        );
        getCourseSettingsStub = vi.fn(() => of({ settings: { askUserModeEnabled: true } }));

        await TestBed.configureTestingModule({
            imports: [IrisAssessmentReviewOverviewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { data: of({ course, showStartInClassQuizButton: true }) } },
                { provide: IrisAssessmentReviewHttpService, useValue: { searchAssessmentReviewParticipations: searchParticipationsStub } },
                { provide: IrisSettingsService, useValue: { getCourseSettingsWithRateLimit: getCourseSettingsStub } },
                { provide: AlertService, useValue: { error: vi.fn(), addAlert: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(IrisAssessmentReviewOverviewComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, FaIconComponent, IrisAssessmentReviewExerciseComponent, HelpIconComponent, SearchFilterComponent] },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe, (key: string) => key),
                        MockComponent(FaIconComponent),
                        MockComponent(IrisAssessmentReviewExerciseComponent),
                        MockComponent(HelpIconComponent),
                        MockComponent(SearchFilterComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisAssessmentReviewOverviewComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should load and group participations by exercise on initialization', () => {
        expect(getCourseSettingsStub).toHaveBeenCalledExactlyOnceWith(7);
        expect(searchParticipationsStub).toHaveBeenCalledExactlyOnceWith(
            7,
            {
                page: 0,
                pageSize: 50,
                sortingOrder: SortingOrder.ASCENDING,
                sortedColumn: 'id',
                searchTerm: '',
                filterProps: [],
            },
            true,
        );
        expect((component as any).exercises()).toHaveLength(1);
        expect((component as any).exercises()[0].participations).toEqual([participation]);
        expect((component as any).totalRows()).toBe(1);
        expect((component as any).participationsPerFilter().get(FilterProp.SUSPICIOUS)).toBe(1);
        expect((component as any).isLoading()).toBe(false);
    });

    it('should reload from the first page when filters change', () => {
        searchParticipationsStub.mockClear();
        (component as any).first.set(50);

        component.updateParticipationFilters([FilterProp.SUSPICIOUS]);

        expect((component as any).first()).toBe(0);
        expect(searchParticipationsStub).toHaveBeenCalledOnce();
        expect(searchParticipationsStub.mock.calls[0][1].filterProps).toEqual([FilterProp.SUSPICIOUS]);
    });

    it('should load the selected page when the paginator page changes', () => {
        searchParticipationsStub.mockClear();

        component.onPageChange(2);

        expect((component as any).first()).toBe(100);
        expect((component as any).rows()).toBe(50);
        expect(searchParticipationsStub).toHaveBeenCalledOnce();
        expect(searchParticipationsStub.mock.calls[0][1].page).toBe(2);
        expect(searchParticipationsStub.mock.calls[0][1].pageSize).toBe(50);
    });

    it('should reset to the first page and reload when the paginator page size changes', () => {
        (component as any).first.set(100);
        searchParticipationsStub.mockClear();

        component.onPageSizeChange(100);

        expect((component as any).first()).toBe(0);
        expect((component as any).rows()).toBe(100);
        expect(searchParticipationsStub).toHaveBeenCalledOnce();
        expect(searchParticipationsStub.mock.calls[0][1].page).toBe(0);
        expect(searchParticipationsStub.mock.calls[0][1].pageSize).toBe(100);
    });

    it('should debounce searches and reload from the first page', async () => {
        vi.useFakeTimers();
        searchParticipationsStub.mockClear();
        (component as any).first.set(50);

        component.onSearch('student1');
        expect(searchParticipationsStub).not.toHaveBeenCalled();

        await vi.advanceTimersByTimeAsync(300);

        expect((component as any).first()).toBe(0);
        expect(searchParticipationsStub).toHaveBeenCalledOnce();
        expect(searchParticipationsStub.mock.calls[0][1].searchTerm).toBe('student1');
    });

    it('should not load participations when ask-user mode is disabled in settings', async () => {
        getCourseSettingsStub.mockReturnValue(of({ settings: { askUserModeEnabled: false } }));
        searchParticipationsStub.mockClear();

        fixture = TestBed.createComponent(IrisAssessmentReviewOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        expect(searchParticipationsStub).not.toHaveBeenCalled();
        expect((component as any).isLoading()).toBe(false);
    });

    it('should report an error when loading Iris settings fails', async () => {
        fixture.destroy();
        getCourseSettingsStub.mockReturnValue(throwError(() => new Error('settings failed')));
        searchParticipationsStub.mockClear();
        const alertSpy = vi.spyOn(alertService, 'addAlert');

        fixture = TestBed.createComponent(IrisAssessmentReviewOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        expect(searchParticipationsStub).not.toHaveBeenCalled();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect((component as any).isLoading()).toBe(false);
    });

    it('should report an error when participation loading fails', () => {
        searchParticipationsStub.mockReturnValue(throwError(() => new Error('failed')));
        const alertSpy = vi.spyOn(alertService, 'addAlert');

        component.refresh();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect((component as any).isLoading()).toBe(false);
    });
});
