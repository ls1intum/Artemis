import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { mockExercise } from 'test/helpers/mocks/service/mock-team.service';

describe('CollapsableAssessmentInstructionsComponent', () => {
    let component: CollapsableAssessmentInstructionsComponent;
    let fixture: ComponentFixture<CollapsableAssessmentInstructionsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            schemas: [NO_ERRORS_SCHEMA],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CollapsableAssessmentInstructionsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', mockExercise);
    });

    it('should receive input properties correctly', () => {
        fixture.componentRef.setInput('isAssessmentTraining', true);
        fixture.componentRef.setInput('showAssessmentInstructions', false);
        fixture.componentRef.setInput('collapsed', true);
        fixture.componentRef.setInput('readOnly', true);

        expect(component.isAssessmentTraining()).toBeTrue();
        expect(component.showAssessmentInstructions()).toBeFalse();
        expect(component.collapsed()).toBeTrue();
        expect(component.readOnly()).toBeTrue();
    });
});
