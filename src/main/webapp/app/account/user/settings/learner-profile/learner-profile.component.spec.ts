import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { LearnerProfileComponent } from './learner-profile.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { LearnerProfileApiService } from './learner-profile-api.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { LearnerProfileDTO } from './dto/learner-profile-dto.model';

describe('LearnerProfileComponent', () => {
    let component: LearnerProfileComponent;
    let fixture: ComponentFixture<LearnerProfileComponent>;
    let profileService: ProfileService;
    let featureToggleService: FeatureToggleService;
    let learnerProfileApiService: LearnerProfileApiService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearnerProfileComponent],
            providers: [
                SessionStorageService,
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                MockProvider(LearnerProfileApiService),
                MockProvider(CourseManagementService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LearnerProfileComponent);
        component = fixture.componentInstance;
        profileService = TestBed.inject(ProfileService);
        featureToggleService = TestBed.inject(FeatureToggleService);
        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the component', () => {
        const compiled = fixture.nativeElement;
        expect(compiled).toBeTruthy();
    });

    describe('module gating', () => {
        /** Marks exactly the given module features active, so a test states the whole module configuration it means. */
        const setActiveModules = (features: string[]) => vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature) => features.includes(feature));

        const render = async () => {
            await component.ngOnInit();
            fixture.detectChanges();
        };

        const query = (selector: string) => fixture.nativeElement.querySelector(selector);

        /**
         * Counts only the separators this template emits between its own sections. The section components render
         * separators of their own, so a plain `hr` query would count those too.
         */
        const separatorCount = () => [...fixture.nativeElement.children].filter((element: Element) => element.tagName === 'HR').length;

        it('should not request the learner profile nor render the atlas sections when the atlas module is inactive', async () => {
            const getLearnerProfile = vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser');
            setActiveModules([MODULE_FEATURE_IRIS]);

            await render();

            expect(component.atlasEnabled()).toBe(false);
            // Both endpoints are @Conditional(AtlasEnabled) on the server, so any request would answer 404
            expect(getLearnerProfile).not.toHaveBeenCalled();
            expect(query('jhi-feedback-learner-profile')).toBeFalsy();
            expect(query('jhi-course-learner-profile')).toBeFalsy();
            // The iris-backed section still loads, which is what keeps the tab worth showing without atlas
            expect(component.insightsEnabled()).toBe(true);
            expect(query('jhi-insights-learner-profile')).toBeTruthy();
            expect(separatorCount()).toBe(0);
        });

        it('should hide the insights section when the iris module is inactive despite the Memiris toggle being on', async () => {
            setActiveModules([MODULE_FEATURE_ATLAS]);
            vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(new LearnerProfileDTO({ id: 1 }));
            vi.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockResolvedValue([]);

            await render();

            // IrisMemoryResource needs @Conditional(IrisEnabled) as well as the toggle, and the toggle is seeded
            // independently of the module, so the toggle alone must not open the section
            expect(component.insightsEnabled()).toBe(false);
            expect(query('jhi-insights-learner-profile')).toBeFalsy();
            // feedback, separator, course
            expect(separatorCount()).toBe(1);
        });

        it('should render no section at all when neither module backs one', async () => {
            setActiveModules([]);
            featureToggleService.setFeatureToggleState(FeatureToggle.Memiris, false);

            await render();

            expect(component.atlasEnabled()).toBe(false);
            expect(component.insightsEnabled()).toBe(false);
            expect(query('jhi-feedback-learner-profile')).toBeFalsy();
            expect(query('jhi-course-learner-profile')).toBeFalsy();
            expect(query('jhi-insights-learner-profile')).toBeFalsy();
            expect(separatorCount()).toBe(0);
        });

        it('should request the learner profile and render every section when both modules are active', async () => {
            const getLearnerProfile = vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(new LearnerProfileDTO({ id: 1 }));
            vi.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockResolvedValue([]);
            setActiveModules([MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS]);

            await render();

            expect(component.atlasEnabled()).toBe(true);
            expect(component.coursePanelEnabled()).toBe(true);
            expect(getLearnerProfile).toHaveBeenCalled();
            expect(query('jhi-feedback-learner-profile')).toBeTruthy();
            expect(query('jhi-course-learner-profile')).toBeTruthy();
            expect(query('jhi-insights-learner-profile')).toBeTruthy();
            // feedback, separator, course, separator, insights
            expect(separatorCount()).toBe(2);
        });

        it('should report a failing atlas request as information and still open the course panel', async () => {
            vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' }));
            vi.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockResolvedValue([]);
            setActiveModules([MODULE_FEATURE_ATLAS]);
            const info = vi.spyOn(TestBed.inject(AlertService), 'info');

            await render();

            expect(info).toHaveBeenCalledWith('artemisApp.learnerProfile.loadFailed');
            // A failed base request must not keep the course section from loading on its own
            expect(component.coursePanelEnabled()).toBe(true);
        });
    });
});
