import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { CourseHomeQuestionsComponent } from './course-home-questions.component';
import { createCourseHomeFixture } from './test/course-home.fixtures';
import en from '../../../../i18n/en/courseHome.json';

describe('Home question summaries', () => {
    it('keeps resolved state and unread state visible together without marking read', async () => {
        TestBed.configureTestingModule({ providers: [provideRouter([]), provideTranslateService()] });
        const translate = TestBed.inject(TranslateService);
        translate.setTranslation('en', en);
        translate.use('en');
        const fixture = TestBed.createComponent(CourseHomeQuestionsComponent);
        const questions = createCourseHomeFixture('unread-replies').questions;
        questions.items![0].state = 'RESOLVED';
        fixture.componentRef.setInput('courseId', 42);
        fixture.componentRef.setInput('questions', questions);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Resolved');
        expect(fixture.nativeElement.textContent).toContain('2 unread replies');
        expect(fixture.nativeElement.querySelector('a[href*="messageId=21"]').getAttribute('href')).toContain('focusReplyId=23');
    });
});
