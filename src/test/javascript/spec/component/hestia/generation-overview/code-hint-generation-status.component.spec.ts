import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeHintGenerationStep } from 'app/entities/hestia/code-hint-model';
import { CodeHintGenerationStatusComponent } from 'app/exercises/programming/hestia/generation-overview/code-hint-generation-status/code-hint-generation-status.component';

describe('CodeHintGenerationStatus Component', () => {
    let comp: CodeHintGenerationStatusComponent;
    let fixture: ComponentFixture<CodeHintGenerationStatusComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();
        fixture = TestBed.createComponent(CodeHintGenerationStatusComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should select step', () => {
        const stepChangeSpy = jest.spyOn(comp.onStepChange, 'emit');
        comp.onSelectStep(CodeHintGenerationStep.GIT_DIFF);
        expect(stepChangeSpy).toHaveBeenCalledOnce();
        expect(stepChangeSpy).toHaveBeenCalledWith(CodeHintGenerationStep.GIT_DIFF);
    });
});
