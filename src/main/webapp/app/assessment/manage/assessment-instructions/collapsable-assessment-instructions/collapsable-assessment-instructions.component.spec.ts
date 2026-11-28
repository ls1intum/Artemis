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

    it('should render the TUM UI panel with an accessible disclosure button', () => {
        const panel = fixture.debugElement.query(By.css('tumaet-ui-panel'));
        const toggle = fixture.debugElement.query(By.css('.tumaet-ui-panel-toggler')).nativeElement as HTMLButtonElement;

        expect(panel).not.toBeNull();
        expect(toggle.getAttribute('aria-label')).toBe('artemisApp.assessmentInstructions.instructions.instructions');
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
    });

    it('should collapse and reopen the panel from its disclosure button', () => {
        const toggle = fixture.debugElement.query(By.css('.tumaet-ui-panel-toggler')).nativeElement as HTMLButtonElement;
        toggle.click();
        fixture.detectChanges();

        expect(component.collapsed()).toBe(true);
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        toggle.click();
        fixture.detectChanges();

        expect(component.collapsed()).toBe(false);
    });

    it('should expose icons', () => {
        expect(component.farListAlt).toBeDefined();
    });
});
