import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, TemplateRef, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTitleBarActionsDirective } from './course-title-bar-actions.directive';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';

@Component({
    template: `<div *titleBarActions>Test Actions</div>`,
    imports: [CourseTitleBarActionsDirective],
})
class TestHostComponent {
    directive = viewChild.required(CourseTitleBarActionsDirective);
    tpl = viewChild.required(TemplateRef<any>);
}

describe('CourseTitleBarActionsDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let mockService: {
        setActionsTemplate: ReturnType<typeof vi.fn>;
        actionsTemplate: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        mockService = {
            setActionsTemplate: vi.fn(),
            actionsTemplate: vi.fn(),
        };

        TestBed.configureTestingModule({
            imports: [TestHostComponent, CourseTitleBarActionsDirective],
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
        expect(mockService.setActionsTemplate).toHaveBeenCalledWith(host.tpl());
    });

    it('does not clear the service template on destroy when it differs', () => {
        fixture.detectChanges();
        const otherTpl = {} as TemplateRef<any>;
        mockService.actionsTemplate.mockReturnValue(otherTpl);

        mockService.setActionsTemplate.mockClear();
        fixture.destroy();

        expect(mockService.setActionsTemplate).not.toHaveBeenCalled();
    });
});
