import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChecklistPanelComponent } from './checklist-panel.component';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { of, throwError } from 'rxjs';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ChecklistPanelComponent', () => {
    let component: ChecklistPanelComponent;
    let fixture: ComponentFixture<ChecklistPanelComponent>;
    let apiService: HyperionProblemStatementApiService;
    let alertService: AlertService;

    const exercise = new ProgrammingExercise(undefined, undefined);
    exercise.id = 123;
    exercise.problemStatement = 'Problem statement';
    exercise.difficulty = DifficultyLevel.EASY; // Changed 'EASY' to DifficultyLevel.EASY for consistency with mockExercise

    const mockResponse: ChecklistAnalysisResponse = {
        inferredLearningGoals: [{ skill: 'Loops', taxonomyLevel: 'APPLY', confidence: 0.9, explanation: 'Explanation' }],
        suggestedDifficulty: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true },
        qualityIssues: [],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, ChecklistPanelComponent], // Component is standout or imports modules? It is standalone: true
            declarations: [MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [MockProvider(HyperionProblemStatementApiService), MockProvider(AlertService), MockProvider(TranslateService)],
        })
            .overrideComponent(ChecklistPanelComponent, {
                remove: { imports: [ArtemisTranslatePipe, TranslateDirective] },
                add: { imports: [MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ChecklistPanelComponent);
        component = fixture.componentInstance;
        apiService = TestBed.inject(HyperionProblemStatementApiService);
        alertService = TestBed.inject(AlertService);

        component.exercise = exercise;
        component.problemStatement = 'Problem statement';
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call analyzeChecklist on button click', () => {
        const analyzeSpy = jest.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(new HttpResponse({ body: mockResponse })));

        // Find analyze button
        const button = fixture.debugElement.query(By.css('button'));
        expect(button).toBeTruthy();
        button.nativeElement.click();

        expect(component.isLoading()).toBeTrue();
        expect(analyzeSpy).toHaveBeenCalledWith(exercise.id!, expect.objectContaining({ problemStatement: 'Problem statement' }));

        // Wait for observable
        fixture.detectChanges();

        expect(component.isLoading()).toBeFalse();
        expect(component.analysisResult()).toEqual(mockResponse);
        expect(component.analysisResult()).toBeDefined();
    });

    it('should handle analysis error', () => {
        jest.spyOn(apiService, 'analyzeChecklist').mockReturnValue(throwError(() => new Error('Error')));
        const errorSpy = jest.spyOn(alertService, 'error');

        component.analyze();

        expect(component.isLoading()).toBeFalse();
        expect(errorSpy).toHaveBeenCalled();
        expect(component.analysisResult()).toBeUndefined();
    });

    it('should display results when available', () => {
        component.analysisResult.set(mockResponse);
        fixture.detectChanges();

        const goalsSection = fixture.debugElement.query(By.css('.analysis-results'));
        expect(goalsSection).toBeTruthy();
        expect(component.analysisResult()).toBeDefined();
    });
});
