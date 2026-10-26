import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { UserSettingsV2SidebarComponent } from './user-settings-v2-sidebar.component';
import { LayoutService } from 'app/foundation/breakpoints/layout.service';

describe('UserSettingsV2SidebarComponent', () => {
    let fixture: ComponentFixture<UserSettingsV2SidebarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsV2SidebarComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: LayoutService,
                    useValue: {
                        subscribeToLayoutChanges: vi.fn().mockReturnValue(of([])),
                        isBreakpointActive: vi.fn().mockReturnValue(true),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(UserSettingsV2SidebarComponent);
    });

    it('shows only the three navigation entries that always have content by default', () => {
        fixture.detectChanges();

        const links = fixture.nativeElement.querySelectorAll('nav a');
        expect(Array.from(links, (link: HTMLAnchorElement) => link.getAttribute('href'))).toEqual([
            '/user-settings-v2/profile',
            '/user-settings-v2/credentials',
            '/user-settings-v2/exercises',
        ]);
    });

    it('shows Learning and AI Experience when their content is available', () => {
        fixture.componentRef.setInput('isLearningAvailable', true);
        fixture.componentRef.setInput('isAiExperienceAvailable', true);
        fixture.detectChanges();

        const links = fixture.nativeElement.querySelectorAll('nav a');
        expect(Array.from(links, (link: HTMLAnchorElement) => link.getAttribute('href'))).toEqual([
            '/user-settings-v2/profile',
            '/user-settings-v2/credentials',
            '/user-settings-v2/learning',
            '/user-settings-v2/ai-experience',
            '/user-settings-v2/exercises',
        ]);
    });

    it('emits when the collapse button is activated', () => {
        const toggleSpy = vi.fn();
        fixture.componentInstance.toggleCollapseState.subscribe(toggleSpy);
        fixture.detectChanges();

        fixture.nativeElement.querySelector('[data-testid="user-settings-v2-sidebar-collapse"]').click();

        expect(toggleSpy).toHaveBeenCalledOnce();
    });
});
