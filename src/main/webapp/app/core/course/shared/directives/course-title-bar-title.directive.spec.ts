// course-title-bar-title.directive.spec.ts
import { Component, TemplateRef, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTitleBarTitleDirective } from './course-title-bar-title.directive';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';

@Component({
    template: `<ng-template titleBarTitle>Test Title</ng-template>`,
    imports: [CourseTitleBarTitleDirective],
})
class TestHostComponent {
    directive = viewChild.required(CourseTitleBarActionsDirective);
    tpl = viewChild.required(TemplateRef<any>);
}

describe('CourseTitleBarTitleDirective', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let mockService: {
        setTitleTemplate: jest.Mock<void, [TemplateRef<any>?]>;
        titleTemplate: jest.Mock<TemplateRef<any> | undefined, []>;
    };

    beforeEach(() => {
        mockService = {
            setTitleTemplate: jest.fn(),
            titleTemplate: jest.fn(),
        };

        TestBed.configureTestingModule({
            imports: [TestHostComponent, CourseTitleBarTitleDirective],
            providers: [{ provide: CourseTitleBarService, useValue: mockService }],
        });

        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
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
