import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { ArtemisTestModule } from '../../../../test.module';

describe('ExerciseUpdatePlagiarismComponent', () => {
    let component: ExerciseUpdatePlagiarismComponent;
    let fixture: ComponentFixture<ExerciseUpdatePlagiarismComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, NgbTooltipMocksModule],
            declarations: [ExerciseUpdatePlagiarismComponent, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();
        fixture = TestBed.createComponent(ExerciseUpdatePlagiarismComponent);
        component = fixture.componentInstance;
    });

    it('should set minimumSizeTooltip on init', () => {
        component.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        fixture.detectChanges();
        expect(component.minimumSizeTooltip).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should set default plagiarism detection config on init if not set', () => {
        component.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        fixture.detectChanges();
        expect(component.exercise.plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should enable cpc', () => {
        component.exercise = {
            plagiarismDetectionConfig: { continuousPlagiarismControlEnabled: false, continuousPlagiarismControlPostDueDateChecksEnabled: false },
        } as Exercise;
        component.toggleCPCEnabled();
        expect(component.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeTrue();
        expect(component.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeTrue();
    });

    it('should disable cpc', () => {
        component.exercise = {
            plagiarismDetectionConfig: { continuousPlagiarismControlEnabled: true, continuousPlagiarismControlPostDueDateChecksEnabled: true },
        } as Exercise;
        component.toggleCPCEnabled();
        expect(component.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeFalse();
        expect(component.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeFalse();
    });

    it('should get correct minimumSizeTooltip for programming exercises', () => {
        component.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        expect(component.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should get correct minimumSizeTooltip for text exercises', () => {
        component.exercise = { type: ExerciseType.TEXT } as Exercise;
        expect(component.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });

    it('should get correct minimumSizeTooltip for modeling exercises', () => {
        component.exercise = { type: ExerciseType.MODELING } as Exercise;
        expect(component.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipModelingExercise');
    });
});
