import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective } from 'ng-mocks';
import { RouterModule } from '@angular/router';

import { OnboardingExploreComponent } from './onboarding-explore.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { By } from '@angular/platform-browser';

describe('OnboardingExploreComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: OnboardingExploreComponent;
    let fixture: ComponentFixture<OnboardingExploreComponent>;
    let course: Course;
    let profileService: ProfileService;

    beforeEach(async () => {
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';

        await TestBed.configureTestingModule({
            imports: [OnboardingExploreComponent, RouterModule.forRoot([])],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideComponent(OnboardingExploreComponent, {
                remove: { imports: [TranslateDirective] },
                add: { imports: [MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(OnboardingExploreComponent);
        fixture.componentRef.setInput('course', course);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with the provided course', () => {
        expect(comp).toBeTruthy();
        expect(comp.course()).toEqual(course);
    });

    it('should render all explore cards when all modules are enabled', () => {
        const cards = fixture.debugElement.queryAll(By.css('.explore-card'));
        expect(cards.length).toBe(6);
    });

    it('should have links with correct router paths', () => {
        const links = fixture.debugElement.queryAll(By.css('a'));
        expect(links.length).toBe(6);

        const hrefs = links.map((link) => link.attributes['href'] || link.properties['href']);
        expect(hrefs).toContain(`/course-management/${course.id}/exercises`);
        expect(hrefs).toContain(`/course-management/${course.id}/lectures`);
        expect(hrefs).toContain(`/course-management/${course.id}/tutorial-groups`);
        expect(hrefs).toContain(`/course-management/${course.id}/exams`);
        expect(hrefs).toContain(`/course-management/${course.id}/competency-management`);
        expect(hrefs).toContain(`/course-management/${course.id}/faqs`);
    });
});
