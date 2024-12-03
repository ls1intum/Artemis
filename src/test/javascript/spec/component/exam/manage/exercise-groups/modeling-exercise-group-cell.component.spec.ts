import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseType } from 'app/entities/exercise.model';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { UMLDiagramType } from '@ls1intum/apollon';

describe('Modeling Exercise Group Cell Component', () => {
    let fixture: ComponentFixture<ModelingExerciseGroupCellComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TranslatePipeMock],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingExerciseGroupCellComponent);
            });
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
