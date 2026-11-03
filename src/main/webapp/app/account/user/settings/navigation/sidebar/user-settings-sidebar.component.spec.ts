import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { UserSettingsSidebarComponent } from './user-settings-sidebar.component';
import { LayoutService } from 'app/foundation/breakpoints/layout.service';

describe('UserSettingsSidebarComponent', () => {
    let fixture: ComponentFixture<UserSettingsSidebarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsSidebarComponent],
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

        fixture = TestBed.createComponent(UserSettingsSidebarComponent);
    });

    it('shows only the three navigation entries that always have content by default', () => {
        fixture.detectChanges();

        const links = fixture.nativeElement.querySelectorAll('nav a');
        expect(Array.from(links, (link: HTMLAnchorElement) => link.getAttribute('href'))).toEqual([
            '/user-settings/profile',
            '/user-settings/credentials',
            '/user-settings/exercises',
        ]);
    });

    it('shows Learning and AI Experience when their content is available', () => {
        fixture.componentRef.setInput('isLearningAvailable', true);
        fixture.componentRef.setInput('isAiExperienceAvailable', true);
        fixture.detectChanges();

        const links = fixture.nativeElement.querySelectorAll('nav a');
        expect(Array.from(links, (link: HTMLAnchorElement) => link.getAttribute('href'))).toEqual([
            '/user-settings/profile',
            '/user-settings/credentials',
            '/user-settings/learning',
            '/user-settings/ai-experience',
            '/user-settings/exercises',
        ]);
    });

    it('emits when the collapse button is activated', () => {
        const toggleSpy = vi.fn();
        fixture.componentInstance.toggleCollapseState.subscribe(toggleSpy);
        fixture.detectChanges();

        fixture.nativeElement.querySelector('[data-testid="user-settings-sidebar-collapse"]').click();

        expect(toggleSpy).toHaveBeenCalledOnce();
    });
});
