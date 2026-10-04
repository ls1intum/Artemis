import { Component, TemplateRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTitleBarToolbarDirective } from 'app/course/shared/directives/course-title-bar-toolbar.directive';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';

@Component({
    selector: 'jhi-test-host',
    template: `<ng-template titleBarToolbar>content</ng-template>`,
    imports: [CourseTitleBarToolbarDirective],
})
class TestHostComponent {}

describe('CourseTitleBarToolbarDirective', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let service: CourseTitleBarService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TestHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(TestHostComponent);
        service = TestBed.inject(CourseTitleBarService);
    });

    it('registers its template as the toolbar template on creation', () => {
        fixture.detectChanges();
        expect(service.toolbarTemplate()).toBeDefined();
    });

    it('clears the toolbar template on destroy if it is still the current one', () => {
        fixture.detectChanges();
        expect(service.toolbarTemplate()).toBeDefined();

        fixture.destroy();

        expect(service.toolbarTemplate()).toBeUndefined();
    });

    it('does not clear the toolbar template on destroy if another instance took it over', () => {
        fixture.detectChanges();
        const otherTemplate = {} as TemplateRef<unknown>;
        service.setToolbarTemplate(otherTemplate);

        fixture.destroy();

        expect(service.toolbarTemplate()).toBe(otherTemplate);
    });
});
