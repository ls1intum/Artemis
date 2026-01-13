import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { AdminTitleBarTitleDirective } from './admin-title-bar-title.directive';
import { AdminTitleBarService } from './admin-title-bar.service';

@Component({
    template: `<ng-template adminTitleBarTitle>Test Title</ng-template>`,
    imports: [AdminTitleBarTitleDirective],
})
class TestHostComponent {}

describe('AdminTitleBarTitleDirective', () => {
    setupTestBed({ zoneless: true });

    let service: AdminTitleBarService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TestHostComponent],
            providers: [AdminTitleBarService],
        });
        service = TestBed.inject(AdminTitleBarService);
    });

    it('should register template with service on creation', () => {
        const setTemplateSpy = vi.spyOn(service, 'setTitleTemplate');

        const fixture = TestBed.createComponent(TestHostComponent);
        fixture.detectChanges();

        expect(setTemplateSpy).toHaveBeenCalled();
        expect(service.titleTemplate()).toBeDefined();
    });

    it('should clear template from service on destroy', () => {
        const fixture = TestBed.createComponent(TestHostComponent);
        fixture.detectChanges();

        const registeredTemplate = service.titleTemplate();
        expect(registeredTemplate).toBeDefined();

        fixture.destroy();

        expect(service.titleTemplate()).toBeUndefined();
    });

    it('should not clear template if service has different template', () => {
        const fixture = TestBed.createComponent(TestHostComponent);
        fixture.detectChanges();

        // Replace with a different template
        const differentTemplate = {} as any;
        service.setTitleTemplate(differentTemplate);

        fixture.destroy();

        // Should still have the different template
        expect(service.titleTemplate()).toBe(differentTemplate);
    });
});
