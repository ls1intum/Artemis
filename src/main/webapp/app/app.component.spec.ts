import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { NgClass, NgStyle } from '@angular/common';
import { RouterModule, RouterOutlet } from '@angular/router';
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
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';

class MockThemeService {
    initialize() {
        return of();
    }
}

describe('AppComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<AppComponent>;
    let comp: AppComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AppComponent, RouterModule.forRoot([])],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ExamParticipationService, useValue: { examIsStarted$: of(false), testRunStarted$: of(false) } },
                { provide: LtiService, useValue: { isShownViaLti$: of(false) } },
                { provide: FeatureToggleService, useValue: { getFeatureToggleActive: () => of(false) } },
                { provide: SentryErrorHandler, useValue: { initSentry: vi.fn() } },
                { provide: JhiLanguageHelper, useValue: { updateTitle: vi.fn() } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // The standalone AppComponent template pulls in many heavy child components; replace them with mocks.
            .overrideComponent(AppComponent, {
                set: {
                    imports: [
                        NgClass,
                        NgStyle,
                        RouterOutlet,
                        CdkScrollable,
                        MockComponent(AlertOverlayComponent),
                        MockComponent(PageRibbonComponent),
                        MockComponent(FooterComponent),
                        MockComponent(CourseNotificationPopupOverlayComponent),
                        MockComponent(LLMSelectionModalComponent),
                        MockComponent(GlobalSearchModalComponent),
                        MockComponent(SetupPasskeyModalComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(AppComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display footer if there is no exam', () => {
        comp.isExamStarted = false;
        comp.showSkeleton = true;
        fixture.changeDetectorRef.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).not.toBeNull();
    });

    it('should not display footer during an exam', () => {
        comp.isExamStarted = true;
        comp.showSkeleton = true;
        fixture.changeDetectorRef.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).toBeNull();
    });

    it('should not render the passkey modal on routes without skeleton', () => {
        comp.showSkeleton = false;
        fixture.changeDetectorRef.detectChanges();

        const passkeyModalElement = fixture.debugElement.query(By.css('jhi-setup-passkey-modal'));

        expect(passkeyModalElement).toBeNull();
    });
});
