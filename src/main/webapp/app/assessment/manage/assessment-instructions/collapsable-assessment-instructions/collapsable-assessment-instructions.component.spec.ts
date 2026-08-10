import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { mockExercise } from 'test/helpers/mocks/service/mock-team.service';
import { CollapsableAssessmentInstructionsComponent } from './collapsable-assessment-instructions.component';

describe('CollapsableAssessmentInstructionsComponent', () => {
    let component: CollapsableAssessmentInstructionsComponent;
    let fixture: ComponentFixture<CollapsableAssessmentInstructionsComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(CollapsableAssessmentInstructionsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.componentRef.setInput('readOnly', false);
        fixture.detectChanges();
    });

    it('should have default input values', () => {
        expect(component.isAssessmentTraining()).toBe(false);
        expect(component.showAssessmentInstructions()).toBe(true);
        expect(component.collapsed()).toBe(false);
        expect(component.exercise()).toEqual(mockExercise);
    });

    it('should render the expanded panel as a card with a primary header', () => {
        const header = fixture.debugElement.query(By.css('.expanded-instructions .card > .card-header')).nativeElement as HTMLElement;

        expect(header.classList).toContain('bg-primary');
        expect(header.classList).toContain('text-white');
        expect(header.querySelector('.card-title')).not.toBeNull();
    });

    it('should collapse when the card header is clicked and reopen from the collapsed bar', () => {
        const header = fixture.debugElement.query(By.css('.expanded-instructions .card-header')).nativeElement as HTMLElement;
        header.click();
        fixture.detectChanges();

        expect(component.collapsed()).toBe(true);
        expect(fixture.debugElement.query(By.css('.expanded-instructions'))).toBeNull();

        const collapsedBar = fixture.debugElement.query(By.css('.collapsed-instructions')).nativeElement as HTMLElement;
        expect(collapsedBar.classList).toContain('bg-primary');
        collapsedBar.click();
        fixture.detectChanges();

        expect(component.collapsed()).toBe(false);
        expect(fixture.debugElement.query(By.css('.expanded-instructions'))).not.toBeNull();
    });

    it('should provide a named, keyboard-operable resize separator', async () => {
        await fixture.whenStable();
        fixture.detectChanges();

        const container = fixture.debugElement.query(By.css('.expanded-instructions')).nativeElement as HTMLElement;
        const handle = fixture.debugElement.query(By.css('.draggable-left')).nativeElement as HTMLElement;
        expect(handle.getAttribute('aria-label')).toBe('artemisApp.assessmentInstructions.instructions.resize');
        expect(handle.getAttribute('role')).toBe('separator');
        expect(handle.getAttribute('aria-orientation')).toBe('vertical');
        expect(handle.tabIndex).toBe(0);

        handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true, cancelable: true }));
        expect(container.style.width).toBe('215px');
        expect(handle.getAttribute('aria-valuenow')).toBe('215');
    });

    it('should expose icons', () => {
        expect(component.faChevronRight).toBeDefined();
        expect(component.faChevronLeft).toBeDefined();
        expect(component.faGripLinesVertical).toBeDefined();
        expect(component.farListAlt).toBeDefined();
    });
});
