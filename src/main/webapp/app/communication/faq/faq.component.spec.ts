import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaqService } from 'app/communication/faq/faq.service';
import { Faq, FaqState } from 'app/communication/shared/entities/faq.model';

import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FaqComponent } from 'app/communication/faq/faq.component';
import { AlertService } from 'app/shared/service/alert.service';
import { SortService } from 'app/shared/service/sort.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { CourseIrisSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';

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
    let faqComponentFixture: ComponentFixture<FaqComponent>;
    let faqComponent: FaqComponent;

    let faqService: FaqService;
    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;
    let sortService: SortService;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;

    let faq1: Faq;
    let faq2: Faq;
    let faq3: Faq;

    let courseId: number;

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
            imports: [MockModule(BrowserAnimationsModule)],
            declarations: [FaqComponent, MockRouterLinkDirective, MockComponent(CustomExerciseCategoryBadgeComponent)],
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
                        return of(new HttpResponse({ status: 200 }));
                    },
                    findAllCategoriesByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [],
                                status: 200,
                            }),
                        );
                    },
                    applyFilters: () => {
                        return [faq2, faq3];
                    },
                    hasSearchTokens: () => {
                        return true;
                    },
                }),
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                faqComponentFixture = TestBed.createComponent(FaqComponent);
                faqComponent = faqComponentFixture.componentInstance;

                faqService = TestBed.inject(FaqService);
                alertService = TestBed.inject(AlertService);
                sortService = TestBed.inject(SortService);
                irisSettingsService = TestBed.inject(IrisSettingsService);
                profileService = TestBed.inject(ProfileService);
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch faqs when initialized', () => {
        const findAllSpy = jest.spyOn(faqService, 'findAllByCourseId');

        faqComponentFixture.detectChanges();
        expect(findAllSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(faqComponent.faqs).toHaveLength(3);
        expect(faqComponent.faqs).toEqual([faq1, faq2, faq3]);
    });

    it('should catch error if loading fails', () => {
        const error = { status: 404 };
        const findSpy = jest.spyOn(faqService, 'findAllByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        expect(findSpy).toHaveBeenCalled();
        expect(faqComponent.faqs).toBeUndefined();
    });

    it('should delete faq', () => {
        const deleteSpy = jest.spyOn(faqService, 'delete');
        faqComponentFixture.detectChanges();
        faqComponent.deleteFaq(courseId, faq1.id!);
        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(courseId, faq1.id!);
        expect(faqComponent.faqs).toHaveLength(2);
        expect(faqComponent.faqs).not.toContain(faq1);
        expect(faqComponent.faqs).toEqual(faqComponent.filteredFaqs);
    });

    it('should not delete faq on error', () => {
        const error = { status: 404 };
        const deleteSpy = jest.spyOn(faqService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.deleteFaq(courseId, faq1.id!);
        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(courseId, faq1.id!);
        expect(faqComponent.faqs).toHaveLength(3);
        expect(faqComponent.faqs).toContain(faq1);
    });

    it('should toggle filter correctly', () => {
        const toggleFilterSpy = jest.spyOn(faqService, 'toggleFilter');
        faqComponentFixture.detectChanges();
        faqComponent.toggleFilters('category2');
        expect(toggleFilterSpy).toHaveBeenCalledOnce();
        expect(faqComponent.filteredFaqs).toHaveLength(2);
        expect(faqComponent.filteredFaqs).not.toContain(faq1);
        expect(faqComponent.filteredFaqs).toEqual([faq2, faq3]);
    });

    it('should catch error if no categories are found', () => {
        alertServiceStub = jest.spyOn(alertService, 'error');
        const error = { status: 404 };
        jest.spyOn(faqService, 'findAllCategoriesByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should search through already filtered array', () => {
        const searchSpy = jest.spyOn(faqService, 'hasSearchTokens');
        const applyFilterSpy = jest.spyOn(faqService, 'applyFilters');
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
        jest.spyOn(sortService, 'sortByProperty').mockReturnValue([]);
        faqComponent.sortRows();
        expect(sortService.sortByProperty).toHaveBeenCalledOnce();
    });

    it('should reject faq properly', () => {
        jest.spyOn(faqService, 'update').mockReturnValue(of(new HttpResponse({ body: faq1 })));
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
        jest.spyOn(faqService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.rejectFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(
            courseId,
            expect.objectContaining({ id: faq1.id, faqState: FaqState.REJECTED, questionTitle: faq1.questionTitle }),
        );
        expect(faq1.faqState).toEqual(FaqState.PROPOSED);
    });

    it('should accepts proposed faq properly', () => {
        jest.spyOn(faqService, 'update').mockReturnValue(of(new HttpResponse({ body: faq1 })));
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
        jest.spyOn(faqService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
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
        const ingestSpy = jest.spyOn(faqService, 'ingestFaqsInPyris').mockImplementation(() => of(new HttpResponse<void>({ status: 200 })));
        faqComponent.ingestFaqsInPyris();
        expect(ingestSpy).toHaveBeenCalledWith(faq1.course?.id);
        expect(ingestSpy).toHaveBeenCalledOnce();
    });

    it('should log error when error occurs', () => {
        alertServiceStub = jest.spyOn(alertService, 'error');
        faqComponent.faqs = [faq1];
        jest.spyOn(faqService, 'ingestFaqsInPyris').mockReturnValue(throwError(() => new Error('Error while ingesting')));
        faqComponent.ingestFaqsInPyris();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should set faqIngestionEnabled based on service response', () => {
        faqComponent.faqs = [faq1];
        const profileInfoResponse = {
            activeProfiles: [PROFILE_IRIS],
        } as ProfileInfo;
        const irisSettingsResponse = {
            courseId: faqComponent.courseId,
            settings: {
                enabled: true,
                customInstructions: '',
                variant: { id: 'DEFAULT' },
                rateLimit: { requests: 100, timeframeHours: 24 },
            },
            effectiveRateLimit: { requests: 100, timeframeHours: 24 },
            applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
        } as CourseIrisSettingsDTO;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoResponse);
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        jest.spyOn(irisSettingsService, 'getCourseSettings').mockImplementation(() => of(irisSettingsResponse));
        faqComponent.ngOnInit();
        expect(irisSettingsService.getCourseSettings).toHaveBeenCalledWith(faqComponent.courseId);
        expect(faqComponent.faqIngestionEnabled).toBeTrue();
    });

    it('should call faq service to enable faq', () => {
        const enableFaqSpy = jest.spyOn(faqService, 'enable').mockReturnValue(of(new HttpResponse<void>()));
        faqComponentFixture.detectChanges();
        faqComponent.enableFaq();
        expect(enableFaqSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(faqComponent.course.faqEnabled).toBeTrue();
    });

    it('should set dialog error source when error while enabling faq', () => {
        const errorResponse = new HttpErrorResponse({
            error: 'Test error',
            status: 500,
            statusText: 'Server Error',
        });
        const enableSpy = jest.spyOn(faqService, 'enable').mockReturnValue(throwError(() => errorResponse));
        let dialogErrorMessage: string | undefined;
        faqComponent.dialogError$.subscribe((msg) => (dialogErrorMessage = msg));
        faqComponentFixture.detectChanges();
        faqComponent.enableFaq();
        expect(enableSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(faqComponent.course.faqEnabled).toBeFalsy();
        expect(dialogErrorMessage).toBe('Http failure response for (unknown url): 500 Server Error');
    });
});
