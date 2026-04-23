import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CoursePrerequisitesButtonComponent } from 'app/core/course/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';

describe('CoursePrerequisitesButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CoursePrerequisitesButtonComponent>;
    let component: CoursePrerequisitesButtonComponent;

    const course1 = {
        id: 1,
        title: 'Course A',
    } as Course;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CoursePrerequisitesButtonComponent, TranslateModule.forRoot()],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CoursePrerequisitesButtonComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set showModal to true when showPrerequisites is called', () => {
        expect(component.showModal()).toBe(false);

        component.showPrerequisites(course1.id!);

        expect(component.showModal()).toBe(true);
    });
});
