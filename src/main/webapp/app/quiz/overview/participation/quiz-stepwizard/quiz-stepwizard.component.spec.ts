import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QuizStepwizardComponent } from './quiz-stepwizard.component';

describe('QuizStepwizardComponent', () => {
    let component: QuizStepwizardComponent;
    let fixture: ComponentFixture<QuizStepwizardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuizStepwizardComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizStepwizardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
