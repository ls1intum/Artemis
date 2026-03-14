import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, TemplateRef, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTitleBarTitleDirective } from './course-title-bar-title.directive';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';

@Component({
    template: `<div *titleBarTitle>Test Title</div>`,
    imports: [CourseTitleBarTitleDirective],
})
class TestHostComponent {
    directive = viewChild.required(CourseTitleBarTitleDirective);
    tpl = viewChild.required(TemplateRef<any>);
}

describe('CourseTitleBarTitleDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let mockService: {
        setTitleTemplate: ReturnType<typeof vi.fn>;
        titleTemplate: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        mockService = {
            setTitleTemplate: vi.fn(),
            titleTemplate: vi.fn(),
        };

        TestBed.configureTestingModule({
            imports: [TestHostComponent, CourseTitleBarTitleDirective],
            providers: [{ provide: CourseTitleBarService, useValue: mockService }],
        });

        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('registers its template with the service on init', () => {
        fixture.detectChanges();
        expect(mockService.setTitleTemplate).toHaveBeenCalledWith(host.tpl());
    });

    it('does not clear the service template on destroy when it differs', () => {
        fixture.detectChanges();
        // Simulate service holding *some other* template
        const otherTpl = {} as TemplateRef<any>;
        mockService.titleTemplate.mockReturnValue(otherTpl);

        mockService.setTitleTemplate.mockClear();
        fixture.destroy();

        expect(mockService.setTitleTemplate).not.toHaveBeenCalled();
    });
});
