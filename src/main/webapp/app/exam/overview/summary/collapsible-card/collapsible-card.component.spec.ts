import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CollapsibleCardComponent } from 'app/exam/overview/summary/collapsible-card/collapsible-card.component';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

let fixture: ComponentFixture<CollapsibleCardComponent>;
let component: CollapsibleCardComponent;

describe('CollapsibleCardComponent', () => {
    setupTestBed({ zoneless: true });

    const toggleSpy = vi.fn();

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();
        fixture = TestBed.createComponent(CollapsibleCardComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('toggleCollapse', toggleSpy);
        fixture.componentRef.setInput('isCardContentCollapsed', false);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should collapse and expand exercise when collapse button is clicked', () => {
        toggleSpy.mockReset();

        fixture.detectChanges();
        const toggleCollapseHeader = fixture.debugElement.query(By.css('.card-header'));

        expect(toggleCollapseHeader).not.toBeNull();

        toggleCollapseHeader.nativeElement.click();
        expect(toggleSpy).toHaveBeenCalledOnce();

        toggleCollapseHeader.nativeElement.click();
        expect(toggleSpy).toHaveBeenCalledTimes(2);

        // Reference component to silence unused-variable warnings.
        expect(component).toBeDefined();
    });
});
