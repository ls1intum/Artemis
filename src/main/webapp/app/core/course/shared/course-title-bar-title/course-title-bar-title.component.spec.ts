import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTitleBarTitleComponent } from './course-title-bar-title.component';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseTitleBarTitleComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseTitleBarTitleComponent;
    let fixture: ComponentFixture<CourseTitleBarTitleComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseTitleBarTitleComponent],
            providers: [
                {
                    provide: TranslateDirective,
                    useClass: MockJhiTranslateDirective,
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTitleBarTitleComponent);
        fixture.componentRef.setInput('title', 'artemisApp.test.defaultIdTitle');
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should use "course-title-bar-title" as default id and apply it to the h5 element', () => {
        fixture.detectChanges();
        expect(component.id()).toBe('course-title-bar-title');
        const h5Element = fixture.debugElement.query(By.css('h5'));
        expect(h5Element).toBeTruthy();
        expect(h5Element.nativeElement.id).toBe('course-title-bar-title');
    });

    it('should allow setting a custom id and apply it to the h5 element', () => {
        const customId = 'my-custom-title-id';
        fixture.componentRef.setInput('id', customId);
        fixture.detectChanges();

        expect(component.id()).toBe(customId);
        const h5Element = fixture.debugElement.query(By.css('h5'));
        expect(h5Element).toBeTruthy();
        expect(h5Element.nativeElement.id).toBe(customId);
    });

    it('should render the title key using the translate directive on a span inside an h5', () => {
        const testTitleKey = 'artemisApp.course.testTitle';
        fixture.componentRef.setInput('title', testTitleKey);
        fixture.detectChanges();

        expect(component.title()).toBe(testTitleKey);

        const h5Element = fixture.debugElement.query(By.css('h5'));
        expect(h5Element).toBeTruthy();
        expect(h5Element.classes['mb-0']).toBe(true);
        expect(h5Element.nativeElement.textContent).toBe(testTitleKey);
    });
});
