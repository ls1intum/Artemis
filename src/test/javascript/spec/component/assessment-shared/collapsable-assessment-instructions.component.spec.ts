import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';

describe('CollapsableAssessmentInstructionsComponent', () => {
    let component: CollapsableAssessmentInstructionsComponent;
    let fixture: ComponentFixture<CollapsableAssessmentInstructionsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CollapsableAssessmentInstructionsComponent],
            schemas: [NO_ERRORS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(CollapsableAssessmentInstructionsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should receive input properties correctly', () => {
        component.isAssessmentTraining = true;
        component.showAssessmentInstructions = false;
        component.collapsed = true;
        component.readOnly = true;

        expect(component.isAssessmentTraining).toBeTrue();
        expect(component.showAssessmentInstructions).toBeFalse();
        expect(component.collapsed).toBeTrue();
        expect(component.readOnly).toBeTrue();
    });
});
