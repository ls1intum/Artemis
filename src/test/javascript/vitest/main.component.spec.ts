import { LocalStorageService } from 'app/shared/storage/local-storage.service';
import { SessionStorageService } from 'app/shared/storage/session-storage.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/angular';
import { AppComponent } from 'app/app.component';
import { AlertOverlayComponent } from 'app/core/alert/alert-overlay.component';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { PageRibbonComponent } from 'app/core/layouts/profiles/page-ribbon.component';

// Mock the initialize method
class MockThemeService {
    // Required because the real ThemeService uses this method, even though it's not called in the tests
    initialize(): void {}
}

// Mock ProfileService
const mockProfileService = {
    getProfileInfo: vi.fn(() =>
        of({
            contact: 'mock-contact',
            git: {
                branch: 'mock-branch',
                commit: {
                    id: { abbrev: 'mock-commit-id' },
                    user: {
                        name: 'test',
                    },
                },
            },
        }),
    ),
};

describe('JhiMainComponent', () => {
    let component: AppComponent;
    let componentFixture: ComponentFixture<AppComponent>;
    let container: Element;

    beforeEach(async () => {
        const { fixture, container: renderedContainer } = await render(AppComponent, {
            declarations: [AlertOverlayComponent, PageRibbonComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                { provide: ProfileService, useValue: mockProfileService }, // Provide the mock ProfileService
                ArtemisTranslatePipe,
                provideHttpClient(),
            ],
        });

        componentFixture = fixture;
        component = fixture.componentInstance;
        container = renderedContainer; // Save the container for querying elements
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should use the initialize method of ThemeService', () => {
        const themeService = TestBed.inject(ThemeService) as MockThemeService;
        themeService.initialize();
    });

    it('should display footer if there is no exam', async () => {
        component.isExamStarted = false;
        component.showSkeleton = true;

        const footerElement = container.querySelector('jhi-footer');
        const notificationPopup = container.querySelector('jhi-notification-popup');

        expect(footerElement).not.toBeNull();
        expect(notificationPopup).not.toBeNull();
    });

    it('should not display footer during an exam', async () => {
        component.isExamStarted = true;
        component.showSkeleton = true;
        component.isTestRunExam = false;
        component.isShownViaLti = false;

        componentFixture.detectChanges(); // Trigger change detection

        const notificationPopup = container.querySelector('jhi-notification-popup');
        const footerElement = container.querySelector('jhi-footer');

        expect(notificationPopup).not.toBeNull();
        expect(footerElement).toBeNull();
    });
});
