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

    it('provides a named, keyboard-operable resize separator', async () => {
        await fixture.whenStable();
        fixture.detectChanges();

        const container = fixture.debugElement.query(By.css('.expanded-instructions')).nativeElement as HTMLElement;
        const handle = fixture.debugElement.query(By.css('.draggable-left')).nativeElement as HTMLElement;
        expect(handle.getAttribute('aria-label')).toBe('artemisApp.assessmentInstructions.instructions.resize');
        expect(handle.getAttribute('role')).toBe('separator');
        expect(handle.getAttribute('aria-orientation')).toBe('vertical');
        expect(handle.tabIndex).toBe(0);

        handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true, cancelable: true }));
        expect(container.style.width).toBe('288px');
        expect(handle.getAttribute('aria-valuenow')).toBe('288');
    });

    it('collapses and reopens through the disclosure control', () => {
        const disclosure = fixture.debugElement.query(By.css('.instructions-disclosure')).nativeElement as HTMLButtonElement;
        disclosure.click();
        fixture.detectChanges();
        expect(component.collapsed()).toBe(true);
        expect(fixture.debugElement.query(By.css('.expanded-instructions'))).toBeNull();

        const reopen = fixture.debugElement.query(By.css('.instructions-disclosure')).nativeElement as HTMLButtonElement;
        reopen.click();
        fixture.detectChanges();
        expect(component.collapsed()).toBe(false);
        expect(fixture.debugElement.query(By.css('.expanded-instructions'))).not.toBeNull();
    });
});
