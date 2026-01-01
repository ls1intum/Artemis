import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { UMLDiagramType } from '@ls1intum/apollon';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Modeling Exercise Group Cell Component', () => {
    let fixture: ComponentFixture<ModelingExerciseGroupCellComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }).compileComponents();
        fixture = TestBed.createComponent(ModelingExerciseGroupCellComponent);
    });

    it('should display diagram type', () => {
        const exercise: ModelingExercise = {
            id: 1,
            type: ExerciseType.MODELING,
            diagramType: UMLDiagramType.ClassDiagram,
        } as any as ModelingExercise;
        fixture.componentRef.setInput('exercise', exercise);

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.DiagramType.' + exercise.diagramType);
    });

    it('should not display anything for other exercise types', () => {
        const exercise: ModelingExercise = {
            id: 1,
            type: ExerciseType.TEXT,
            diagramType: UMLDiagramType.ClassDiagram,
        } as any as ModelingExercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toBe('');
    });
});
