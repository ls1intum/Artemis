import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseType } from 'app/entities/exercise.model';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';

describe('Modeling Exercise Group Cell Component', () => {
    let comp: ModelingExerciseGroupCellComponent;
    let fixture: ComponentFixture<ModelingExerciseGroupCellComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ModelingExerciseGroupCellComponent, TranslatePipeMock],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingExerciseGroupCellComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should display diagram type', () => {
        const exercise: ModelingExercise = {
            id: 1,
            type: ExerciseType.MODELING,
            diagramType: UMLDiagramType.ClassDiagram,
        } as any as ModelingExercise;
        comp.exercise = exercise;

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.DiagramType.' + exercise.diagramType);
    });

    it('should not display anything for other exercise types', () => {
        comp.exercise = {
            id: 1,
            type: ExerciseType.TEXT,
            diagramType: UMLDiagramType.ClassDiagram,
        } as any as ModelingExercise;
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toBe('');
    });
});
