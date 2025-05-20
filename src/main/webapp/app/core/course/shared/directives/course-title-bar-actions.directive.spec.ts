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
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let mockService: {
        setActionsTemplate: jest.Mock<void, [TemplateRef<any>?]>;
        actionsTemplate: jest.Mock<TemplateRef<any> | undefined, []>;
    };

    beforeEach(() => {
        mockService = {
            setActionsTemplate: jest.fn(),
            actionsTemplate: jest.fn(),
        };

        TestBed.configureTestingModule({
            imports: [TestHostComponent, CourseTitleBarActionsDirective],
            providers: [{ provide: CourseTitleBarService, useValue: mockService }],
        });

        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
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
