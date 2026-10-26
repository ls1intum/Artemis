import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { CourseHomeTeachingTeamComponent } from './course-home-teaching-team.component';
import { createCourseHomeFixture } from './test/course-home.fixtures';

describe('Home teaching team', () => {
    it('names staff beside decorative portraits and expands inline with a keyboard button', () => {
        TestBed.configureTestingModule({ providers: [provideTranslateService()] });
        const fixture = TestBed.createComponent(CourseHomeTeachingTeamComponent);
        fixture.componentRef.setInput('team', createCourseHomeFixture('first').teachingTeam);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Ada Lovelace');
        expect(fixture.nativeElement.querySelector('tumaet-ui-avatar').getAttribute('aria-hidden')).toBe('true');
        const expand = vi.fn();
        fixture.componentInstance.loadMore.subscribe(expand);
        fixture.nativeElement.querySelector('button').click();
        expect(expand).toHaveBeenCalledOnce();
        expect(fixture.nativeElement.querySelector('button').getAttribute('aria-controls')).toBe('home-team-members');
    });
});
