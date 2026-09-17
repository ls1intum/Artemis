import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SidebarSubpageItem } from './sidebar-subpage-item';

describe('SidebarSubpageItem', () => {
    let fixture: ComponentFixture<SidebarSubpageItem>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SidebarSubpageItem],
            providers: [provideRouter([])],
        }).compileComponents();

        fixture = TestBed.createComponent(SidebarSubpageItem);
        fixture.componentRef.setInput('icon', faUser);
        fixture.componentRef.setInput('title', 'Test Title');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render link with default detail id and data-testid when subpage is not provided', () => {
        fixture.componentRef.setInput('subjectId', 42);
        fixture.detectChanges();

        const link = fixture.debugElement.query(By.css('a'));
        expect(link).not.toBeNull();
        expect(link.nativeElement.id).toBe('exam-42-detail');
        expect(link.nativeElement.getAttribute('data-testid')).toBe('sidebar-subpage-detail');
        expect(link.nativeElement.textContent).toContain('Test Title');
    });

    it('should render link with custom subpage id and data-testid when subpage is provided', () => {
        fixture.componentRef.setInput('subjectId', 42);
        fixture.componentRef.setInput('subpage', 'exercise-groups');
        fixture.componentRef.setInput('title', 'Exercise Groups');
        fixture.detectChanges();

        const link = fixture.debugElement.query(By.css('a'));
        expect(link).not.toBeNull();
        expect(link.nativeElement.id).toBe('exam-42-exercise-groups');
        expect(link.nativeElement.getAttribute('data-testid')).toBe('sidebar-subpage-exercise-groups');
        expect(link.nativeElement.textContent).toContain('Exercise Groups');
    });
});
