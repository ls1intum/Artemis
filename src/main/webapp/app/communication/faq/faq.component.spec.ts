import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaqService } from 'app/communication/faq/faq.service';
import { Faq, FaqState } from 'app/communication/shared/entities/faq.model';

import { FaqComponent } from 'app/communication/faq/faq.component';
import { AlertService } from 'app/shared/service/alert.service';
import { SortService } from 'app/shared/service/sort.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';

function createFaq(id: number, category: string, color: string): Faq {
    const faq = new Faq();
    faq.id = id;
    faq.questionTitle = 'questionTitle';
    faq.questionAnswer = 'questionAnswer';
    faq.categories = [new FaqCategory(category, color)];
    faq.faqState = FaqState.PROPOSED;
    return faq;
}

describe('FaqComponent', () => {
    setupTestBed({ zoneless: true });

    let faqComponentFixture: ComponentFixture<FaqComponent>;
    let faqComponent: FaqComponent;

    let faqService: FaqService;
    let alertServiceStub: ReturnType<typeof vi.spyOn>;
    let alertService: AlertService;
    let sortService: SortService;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;

    let faq1: Faq;
    let faq2: Faq;
    let faq3: Faq;

    let courseId: number;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        // In beforeEach:
        faq1 = createFaq(1, 'category1', '#94a11c');
        faq2 = createFaq(2, 'category2', '#0ab84f');
        faq3 = createFaq(3, 'category3', '#0ab84f');

        courseId = 1;

        const profileInfo = {
            activeProfiles: [],
        } as unknown as ProfileInfo;

        TestBed.configureTestingModule({
            imports: [FaqComponent, MockRouterLinkDirective, MockComponent(CustomExerciseCategoryBadgeComponent)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: of({ course: { id: 1 } }),
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                { provide: ProfileService, useValue: new MockProfileService() },

                MockProvider(FaqService, {
                    findAllByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [faq1, faq2, faq3],
                                status: 200,
                            }),
                        );
                    },
                    delete: () => {
                        return of(new HttpResponse<void>({ status: 200 }));
                    },
                    findAllCategoriesByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [],
                                status: 200,
                            }),
                        );
                    },
                    convertFaqCategoriesAsStringFromServer: () => [],
                    applyFilters: () => {
                        return [faq2, faq3];
                    },
                    hasSearchTokens: () => {
                        return true;
                    },
                }),
                provideHttpClient(),
            ],
        });

        faqComponentFixture = TestBed.createComponent(FaqComponent);
        faqComponent = faqComponentFixture.componentInstance;

        faqService = TestBed.inject(FaqService);
        alertService = TestBed.inject(AlertService);
        sortService = TestBed.inject(SortService);
        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
    });

    it('should fetch faqs when initialized', () => {
        const findAllSpy = vi.spyOn(faqService, 'findAllByCourseId');

        faqComponentFixture.detectChanges();
        expect(findAllSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(faqComponent.faqs).toHaveLength(3);
        expect(faqComponent.faqs).toEqual([faq1, faq2, faq3]);
    });

    it('should catch error if loading fails', () => {
        const error = { status: 404 };
        const findSpy = vi.spyOn(faqService, 'findAllByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        expect(findSpy).toHaveBeenCalled();
        expect(faqComponent.faqs).toBeUndefined();
    });

    it('should delete faq', () => {
        const deleteSpy = vi.spyOn(faqService, 'delete');
        faqComponentFixture.detectChanges();
        faqComponent.deleteFaq(courseId, faq1.id!);
        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(courseId, faq1.id!);
        expect(faqComponent.faqs).toHaveLength(2);
        expect(faqComponent.faqs).not.toContain(faq1);
        expect(faqComponent.faqs).toEqual(faqComponent.filteredFaqs);
    });

    it('should not delete faq on error', () => {
        const error = { status: 404 };
        const deleteSpy = vi.spyOn(faqService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.deleteFaq(courseId, faq1.id!);
        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(courseId, faq1.id!);
        expect(faqComponent.faqs).toHaveLength(3);
        expect(faqComponent.faqs).toContain(faq1);
    });

    it('should toggle filter correctly', () => {
        const toggleFilterSpy = vi.spyOn(faqService, 'toggleFilter');
        faqComponentFixture.detectChanges();
        faqComponent.toggleFilters('category2');
        expect(toggleFilterSpy).toHaveBeenCalledOnce();
        expect(faqComponent.filteredFaqs).toHaveLength(2);
        expect(faqComponent.filteredFaqs).not.toContain(faq1);
        expect(faqComponent.filteredFaqs).toEqual([faq2, faq3]);
    });

    it('should catch error if no categories are found', () => {
        alertServiceStub = vi.spyOn(alertService, 'error');
        const error = { status: 404 };
        vi.spyOn(faqService, 'findAllCategoriesByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should search through already filtered array', () => {
        const searchSpy = vi.spyOn(faqService, 'hasSearchTokens');
        const applyFilterSpy = vi.spyOn(faqService, 'applyFilters');
        faqComponent.setSearchValue('questionTitle');
        faqComponent.refreshFaqList(faqComponent.searchInput.getValue());
        expect(applyFilterSpy).toHaveBeenCalledOnce();
        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenCalledWith(faq2, 'questionTitle');
        expect(searchSpy).toHaveBeenCalledWith(faq3, 'questionTitle');
        expect(faqComponent.filteredFaqs).toHaveLength(2);
        expect(faqComponent.filteredFaqs).not.toContain(faq1);
        expect(faqComponent.filteredFaqs).toEqual([faq2, faq3]);
    });

    it('should call sortService when sortRows is called', () => {
        vi.spyOn(sortService, 'sortByProperty').mockReturnValue([]);
        faqComponent.sortRows();
        expect(sortService.sortByProperty).toHaveBeenCalledOnce();
    });

    it('should reject faq properly', () => {
        vi.spyOn(faqService, 'update').mockReturnValue(of(new HttpResponse({ body: faq1 })));
        faqComponentFixture.detectChanges();
        faqComponent.rejectFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(
            courseId,
            expect.objectContaining({
                id: faq1.id,
                faqState: FaqState.REJECTED,
                questionTitle: faq1.questionTitle,
                questionAnswer: faq1.questionAnswer,
                categories: faq1.categories,
            }),
        );
        expect(faq1.faqState).toEqual(FaqState.REJECTED);
    });

    it('should not change status if rejection fails', () => {
        const error = { status: 500 };
        vi.spyOn(faqService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.rejectFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(
            courseId,
            expect.objectContaining({ id: faq1.id, faqState: FaqState.REJECTED, questionTitle: faq1.questionTitle }),
        );
        expect(faq1.faqState).toEqual(FaqState.PROPOSED);
    });

    it('should accepts proposed faq properly', () => {
        vi.spyOn(faqService, 'update').mockReturnValue(of(new HttpResponse({ body: faq1 })));
        faqComponentFixture.detectChanges();
        faqComponent.acceptProposedFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(
            courseId,
            expect.objectContaining({
                id: faq1.id,
                faqState: FaqState.ACCEPTED,
                questionTitle: faq1.questionTitle,
                questionAnswer: faq1.questionAnswer,
                categories: faq1.categories,
            }),
        );
        expect(faq1.faqState).toEqual(FaqState.ACCEPTED);
    });

    it('should not change status if acceptance fails', () => {
        const error = { status: 500 };
        vi.spyOn(faqService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.acceptProposedFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(
            courseId,
            expect.objectContaining({ id: faq1.id, faqState: FaqState.ACCEPTED, questionTitle: faq1.questionTitle }),
        );
        expect(faq1.faqState).toEqual(FaqState.PROPOSED);
    });

    it('should call the service to ingest faqs when ingestFaqsInPyris is called', () => {
        faqComponent.faqs = [faq1];
        const ingestSpy = vi.spyOn(faqService, 'ingestFaqsInPyris').mockImplementation(() => of(new HttpResponse<void>({ status: 200 })));
        faqComponent.ingestFaqsInPyris();
        expect(ingestSpy).toHaveBeenCalledWith(faq1.course?.id);
        expect(ingestSpy).toHaveBeenCalledOnce();
    });

    it('should log error when error occurs', () => {
        alertServiceStub = vi.spyOn(alertService, 'error');
        faqComponent.faqs = [faq1];
        vi.spyOn(faqService, 'ingestFaqsInPyris').mockReturnValue(throwError(() => new Error('Error while ingesting')));
        faqComponent.ingestFaqsInPyris();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should set irisEnabled based on service response', () => {
        faqComponent.faqs = [faq1];
        const profileInfoResponse = {
            activeModuleFeatures: [MODULE_FEATURE_IRIS],
        } as ProfileInfo;
        const irisSettingsResponse = {
            courseId: faqComponent.courseId,
            settings: {
                enabled: true,
                customInstructions: '',
                variant: 'default',
                rateLimit: { requests: 100, timeframeHours: 24 },
            },
            effectiveRateLimit: { requests: 100, timeframeHours: 24 },
            applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
        } as IrisCourseSettingsWithRateLimitDTO;
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoResponse);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockImplementation(() => of(irisSettingsResponse));
        faqComponent.ngOnInit();
        expect(irisSettingsService.getCourseSettingsWithRateLimit).toHaveBeenCalledWith(faqComponent.courseId);
        expect(faqComponent.irisEnabled).toBe(true);
    });
});
