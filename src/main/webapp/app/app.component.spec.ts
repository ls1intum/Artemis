import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { NgClass } from '@angular/common';
import { Router, RouterModule, RouterOutlet } from '@angular/router';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { of } from 'rxjs';
import { MockComponent } from 'ng-mocks';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AppComponent } from './app.component';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { AlertOverlayComponent } from 'app/core/alert/alert-overlay.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ClientErrorHandler } from 'app/core/error-reporting/client-error.handler';
import { LazyRouteRecoveryService } from 'app/core/navigation/lazy-route-recovery.service';
import { PageRibbonComponent } from 'app/core/layouts/profiles/page-ribbon.component';
import { FooterComponent } from 'app/core/layouts/footer/footer.component';
import { CourseNotificationPopupOverlayComponent } from 'app/notification/course-notification/course-notification-popup-overlay/course-notification-popup-overlay.component';
import { LLMSelectionModalComponent } from 'app/logos/llm-selection-popup.component';
import { GlobalSearchModalComponent } from 'app/core/navbar/global-search/components/modal/global-search-modal.component';
import { SetupPasskeyModalComponent } from 'app/course/overview/setup-passkey-modal/setup-passkey-modal.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { LtiService } from 'app/foundation/service/lti.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionLoadErrorComponent } from 'app/logos/llm-selection-load-error.component';
import { Alert, AlertService } from 'app/foundation/service/alert.service';
import { WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { AccountService } from 'app/core/auth/account.service';
import { BrowserFingerprintService } from 'app/account/fingerprint/browser-fingerprint.service';
import { User } from 'app/account/user/user.model';

class MockThemeService {
    initialize() {
        return of();
    }
}

describe('AppComponent', () => {
    let fixture: ComponentFixture<AppComponent>;
    let comp: AppComponent;
    let handleNavigationError: ReturnType<typeof vi.fn>;

    async function createAppComponent(deferBlockBehavior = DeferBlockBehavior.Playthrough) {
        handleNavigationError = vi.fn();
        await TestBed.configureTestingModule({
            deferBlockBehavior,
            imports: [
                AppComponent,
                RouterModule.forRoot([
                    // A route whose lazily loaded chunk cannot be fetched, and one whose loader fails the way a
                    // resolver surfaces a missing entity, so both NavigationError branches can be driven for real.
                    { path: 'chunk-failure', loadComponent: () => Promise.reject(new Error('Failed to fetch dynamically imported module: /chunk-ABC123.js')) },
                    { path: 'server-error-404', loadComponent: () => Promise.reject({ status: 404 }) },
                    { path: 'rejected-without-error', loadComponent: () => Promise.reject() },
                ]),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                { provide: ClientErrorHandler, useValue: { reportIfPasskeyIsNotSupported: vi.fn() } },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ExamParticipationService, useValue: { examIsStarted$: of(false), testRunStarted$: of(false) } },
                { provide: LtiService, useValue: { isShownViaLti$: of(false) } },
                {
                    provide: FeatureToggleService,
                    useValue: { getFeatureToggleActive: () => of(false), subscribeFeatureToggleUpdates: () => {}, unsubscribeFeatureToggleUpdates: () => {} },
                },
                { provide: JhiLanguageHelper, useValue: { updateTitle: vi.fn() } },
                { provide: WINDOW_INJECTOR_TOKEN, useValue: { location: { reload: vi.fn() } } },
                { provide: LazyRouteRecoveryService, useValue: { handleNavigationError } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // The standalone AppComponent template pulls in many heavy child components; replace them with mocks.
            .overrideComponent(AppComponent, {
                set: {
                    imports: [
                        NgClass,
                        RouterOutlet,
                        CdkScrollable,
                        MockComponent(AlertOverlayComponent),
                        MockComponent(PageRibbonComponent),
                        MockComponent(FooterComponent),
                        MockComponent(CourseNotificationPopupOverlayComponent),
                        MockComponent(LLMSelectionModalComponent),
                        LLMSelectionLoadErrorComponent,
                        MockComponent(GlobalSearchModalComponent),
                        MockComponent(SetupPasskeyModalComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(AppComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    }

    beforeEach(async () => {
        await createAppComponent();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display footer if there is no exam', () => {
        comp.isExamStarted.set(false);
        comp.showSkeleton.set(true);
        fixture.changeDetectorRef.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).not.toBeNull();
    });

    it('should not display footer during an exam', () => {
        comp.isExamStarted.set(true);
        comp.showSkeleton.set(true);
        fixture.changeDetectorRef.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).toBeNull();
    });

    // Driven through real navigations to the routes registered above, so the router reports the NavigationError the
    // same way it does in the browser rather than the test pushing a synthetic event into its event stream.
    describe('LLM selection popup', () => {
        it('should not create the popup, and so not load its code, before a request to open it', async () => {
            TestBed.inject(AccountService).userIdentity.set({ id: 1, login: 'student' } as User);
            await fixture.whenStable();
            fixture.detectChanges();

            expect(fixture.debugElement.query(By.css('jhi-llm-selection-modal'))).toBeNull();
        });

        it('should create the popup when a signed-in user makes a request to open it, although no popup existed to hear the request', async () => {
            TestBed.inject(AccountService).userIdentity.set({ id: 1, login: 'student' } as User);
            fixture.detectChanges();
            void TestBed.inject(LLMSelectionModalService).open();
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            expect(fixture.debugElement.query(By.css('jhi-llm-selection-modal'))).not.toBeNull();
        });
    });

    describe('when the code of the LLM selection popup cannot be loaded', () => {
        beforeEach(async () => {
            // The defer blocks are driven by hand, so that the test can put the block of the popup into the state of a failed download.
            TestBed.resetTestingModule();
            await createAppComponent(DeferBlockBehavior.Manual);
        });

        it('should answer the request as dismissed and offer a reload, instead of leaving the caller waiting', async () => {
            const addAlert = vi.spyOn(TestBed.inject(AlertService), 'addAlert').mockReturnValue({} as Alert);
            TestBed.inject(AccountService).userIdentity.set({ id: 1, login: 'student' } as User);
            fixture.detectChanges();
            const answer = TestBed.inject(LLMSelectionModalService).open();
            const [popupBlock] = await fixture.getDeferBlocks();

            await popupBlock.render(DeferBlockState.Error);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(await answer).toBe(LLM_MODAL_DISMISSED);
            expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ message: 'artemisApp.llmSelectionPopup.loadFailedAlert' }));
            expect(fixture.debugElement.query(By.css('jhi-llm-selection-modal'))).toBeNull();
        });
    });

    describe('browser fingerprint', () => {
        it('should start the fingerprint for a signed-in user only, so a visitor of the sign-in page never loads it', () => {
            const startWhenIdle = vi.spyOn(TestBed.inject(BrowserFingerprintService), 'startWhenIdle');
            const accountService = TestBed.inject(AccountService);

            fixture.detectChanges();
            expect(startWhenIdle).not.toHaveBeenCalled();

            accountService.userIdentity.set({ id: 1, login: 'student' } as User);
            fixture.detectChanges();

            expect(startWhenIdle).toHaveBeenCalled();
        });
    });

    describe('navigation errors', () => {
        it('should hand a failed route chunk to the recovery service', async () => {
            const router = TestBed.inject(Router);

            await router.navigateByUrl('/chunk-failure').catch(() => {});

            expect(handleNavigationError).toHaveBeenCalledOnce();
            const [error, url] = handleNavigationError.mock.calls[0];
            expect((error as Error).message).toContain('Failed to fetch dynamically imported module');
            expect(url).toBe('/chunk-failure');
        });

        it('should still send a 404 to the not-found page rather than the recovery service', async () => {
            const router = TestBed.inject(Router);
            const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

            await router.navigateByUrl('/server-error-404').catch(() => {});

            expect(navigate).toHaveBeenCalledExactlyOnceWith(['/404']);
            expect(handleNavigationError).not.toHaveBeenCalled();
        });

        // The router types the error as any, so reading a property off it unconditionally would throw here.
        it('should survive a navigation that failed without an error value', async () => {
            const router = TestBed.inject(Router);

            await router.navigateByUrl('/rejected-without-error').catch(() => {});

            expect(handleNavigationError).toHaveBeenCalledExactlyOnceWith(undefined, '/rejected-without-error');
        });
    });
});
