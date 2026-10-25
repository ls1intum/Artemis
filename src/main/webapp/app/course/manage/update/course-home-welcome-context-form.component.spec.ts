import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseHomeWelcomeContextFormComponent } from './course-home-welcome-context-form.component';

describe('Course welcome context form', () => {
    beforeEach(() =>
        TestBed.configureTestingModule({ imports: [CourseHomeWelcomeContextFormComponent], providers: [{ provide: TranslateService, useClass: MockTranslateService }] }),
    );
    it('edits and clears the supplied control and exposes the maximum-length error', () => {
        const fixture = TestBed.createComponent(CourseHomeWelcomeContextFormComponent);
        const control = new FormControl('Learn by doing.', { nonNullable: true, validators: [Validators.maxLength(2000)] });
        fixture.componentRef.setInput('control', control);
        fixture.detectChanges();
        const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea');
        expect(textarea.value).toBe('Learn by doing.');
        expect(textarea.maxLength).toBe(2000);
        textarea.value = '';
        textarea.dispatchEvent(new Event('input'));
        expect(control.value).toBe('');
        control.setValue('x'.repeat(2001));
        control.markAsTouched();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('artemisApp.course.studentWelcomeContext.tooLong');
    });
});
