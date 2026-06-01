import { Component, TemplateRef, signal, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { ExtensionPointDirective } from 'app/foundation/extension-point/extension-point.directive';

@Component({
    template: `
        <ng-template #override let-key="key" let-extra="extra"
            ><span class="override">override-{{ key }}</span
            ><span class="extra">{{ extra }}</span></ng-template
        >
        <div *jhiExtensionPoint="overrideTemplate(); context: context()">default-content</div>
    `,
    imports: [ExtensionPointDirective],
})
class HostComponent {
    readonly overrideRef = viewChild<TemplateRef<any>>('override');
    readonly overrideTemplate = signal<TemplateRef<any> | undefined>(undefined);
    readonly context = signal<{ key: string; extra?: string }>({ key: 'A' });
}

describe('ExtensionPointDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    function text(): string {
        return (fixture.nativeElement.textContent ?? '').trim();
    }

    it('renders the default content when no override template is provided', () => {
        expect(text()).toContain('default-content');
        expect(fixture.nativeElement.querySelector('.override')).toBeNull();
    });

    it('renders the override template (with context) when provided, replacing the default', () => {
        fixture.componentInstance.overrideTemplate.set(fixture.componentInstance.overrideRef());
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.override')?.textContent).toContain('override-A');
        expect(text()).not.toContain('default-content');
    });

    it('updates the rendered output when ONLY the context changes, without recreating the view (zoneless)', () => {
        fixture.componentInstance.overrideTemplate.set(fixture.componentInstance.overrideRef());
        fixture.detectChanges();
        const spanBefore = fixture.nativeElement.querySelector('.override');
        expect(spanBefore?.textContent).toContain('override-A');

        // Change ONLY the context (same template). The in-place context update must re-render under zoneless.
        fixture.componentInstance.context.set({ key: 'B' });
        fixture.detectChanges();
        const spanAfter = fixture.nativeElement.querySelector('.override');
        expect(spanAfter?.textContent).toContain('override-B');
        // The embedded view must NOT be recreated (the recreate effect reads context untracked), so the DOM node is identical.
        expect(spanAfter).toBe(spanBefore);
    });

    it('drops context keys that are removed when the context shrinks', () => {
        fixture.componentInstance.overrideTemplate.set(fixture.componentInstance.overrideRef());
        fixture.componentInstance.context.set({ key: 'A', extra: 'x' });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.extra')?.textContent).toBe('x');

        // Shrinking the context (removing `extra`) must not leave the stale value rendered.
        fixture.componentInstance.context.set({ key: 'B' });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.override')?.textContent).toContain('override-B');
        expect(fixture.nativeElement.querySelector('.extra')?.textContent).toBe('');
    });
});
