import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { mockExercise } from '../../helpers/mocks/service/mock-team.service';

describe('CollapsableAssessmentInstructionsComponent', () => {
    let component: CollapsableAssessmentInstructionsComponent;
    let fixture: ComponentFixture<CollapsableAssessmentInstructionsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            schemas: [NO_ERRORS_SCHEMA],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CollapsableAssessmentInstructionsComponent);
        component = fixture.componentInstance;
        component.exercise = mockExercise;
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
