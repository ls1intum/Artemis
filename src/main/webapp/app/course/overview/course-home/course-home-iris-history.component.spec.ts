import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { CourseHomeIrisHistoryComponent } from './course-home-iris-history.component';
import { createCourseHomeFixture } from './test/course-home.fixtures';

describe('Home Iris history', () => {
    it('links directly to an existing session and needs no chat service', () => {
        TestBed.configureTestingModule({ providers: [provideRouter([]), provideTranslateService()] });
        const fixture = TestBed.createComponent(CourseHomeIrisHistoryComponent);
        fixture.componentRef.setInput('courseId', 42);
        fixture.componentRef.setInput('sessions', createCourseHomeFixture('iris-follow-up').irisSessions);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a[href*="irisSession=31"]').textContent).toContain('Understanding coupling');
    });
});
