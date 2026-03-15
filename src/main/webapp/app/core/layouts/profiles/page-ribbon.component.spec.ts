import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { PageRibbonComponent } from 'app/core/layouts/profiles/page-ribbon.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PROFILE_DEV, PROFILE_TEST } from 'app/app.constants';

describe('PageRibbonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PageRibbonComponent;
    let fixture: ComponentFixture<PageRibbonComponent>;
    let profileService: ProfileService;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [PageRibbonComponent, MockDirective(TranslateDirective)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ProfileService,
                    useValue: {
                        isDevelopment: vi.fn().mockReturnValue(false),
                        isProduction: vi.fn().mockReturnValue(false),
                        isTestServer: vi.fn().mockReturnValue(false),
                    },
                },
            ],
        });
        await TestBed.compileComponents();

        profileService = TestBed.inject(ProfileService);
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(PageRibbonComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should set ribbonEnv to dev when in development mode', () => {
        vi.spyOn(profileService, 'isDevelopment').mockReturnValue(true);
        vi.spyOn(profileService, 'isProduction').mockReturnValue(false);
        vi.spyOn(profileService, 'isTestServer').mockReturnValue(false);

        fixture.detectChanges();

        expect(component.ribbonEnv).toBe(PROFILE_DEV);
    });

    it('should set ribbonEnv to test when in production mode and on test server', () => {
        vi.spyOn(profileService, 'isDevelopment').mockReturnValue(false);
        vi.spyOn(profileService, 'isProduction').mockReturnValue(true);
        vi.spyOn(profileService, 'isTestServer').mockReturnValue(true);

        fixture.detectChanges();

        expect(component.ribbonEnv).toBe(PROFILE_TEST);
    });

    it('should not set ribbonEnv when in production mode but not on test server', () => {
        vi.spyOn(profileService, 'isDevelopment').mockReturnValue(false);
        vi.spyOn(profileService, 'isProduction').mockReturnValue(true);
        vi.spyOn(profileService, 'isTestServer').mockReturnValue(false);

        fixture.detectChanges();

        expect(component.ribbonEnv).toBeUndefined();
    });

    it('should not set ribbonEnv when not in development and not in production', () => {
        vi.spyOn(profileService, 'isDevelopment').mockReturnValue(false);
        vi.spyOn(profileService, 'isProduction').mockReturnValue(false);
        vi.spyOn(profileService, 'isTestServer').mockReturnValue(false);

        fixture.detectChanges();

        expect(component.ribbonEnv).toBeUndefined();
    });

    it('should set ribbonEnv to test when both development and production+testServer conditions are met', () => {
        // Edge case: both conditions are true, the second one (test) should override
        vi.spyOn(profileService, 'isDevelopment').mockReturnValue(true);
        vi.spyOn(profileService, 'isProduction').mockReturnValue(true);
        vi.spyOn(profileService, 'isTestServer').mockReturnValue(true);

        fixture.detectChanges();

        // The component first sets dev, then overwrites with test
        expect(component.ribbonEnv).toBe(PROFILE_TEST);
    });

    it('should display the ribbon when ribbonEnv is set', () => {
        vi.spyOn(profileService, 'isDevelopment').mockReturnValue(true);
        fixture.detectChanges();

        const ribbonElement = fixture.debugElement.nativeElement.querySelector('.ribbon');
        expect(ribbonElement).not.toBeNull();
    });

    it('should not display the ribbon when ribbonEnv is not set', () => {
        vi.spyOn(profileService, 'isDevelopment').mockReturnValue(false);
        vi.spyOn(profileService, 'isProduction').mockReturnValue(false);
        fixture.detectChanges();

        const ribbonElement = fixture.debugElement.nativeElement.querySelector('.ribbon');
        expect(ribbonElement).toBeNull();
    });
});
