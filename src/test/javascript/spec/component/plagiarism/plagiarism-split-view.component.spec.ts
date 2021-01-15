import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';

const collapse = jest.fn();
const setSizes = jest.fn();

jest.mock('split.js', () => ({
    default: jest.fn().mockImplementation(() => ({
        collapse,
        setSizes,
    })),
}));

describe('Plagiarism Split View Component', () => {
    let comp: PlagiarismSplitViewComponent;
    let fixture: ComponentFixture<PlagiarismSplitViewComponent>;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;
    const splitControlSubject = new Subject<string>();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisPlagiarismModule, TranslateTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismSplitViewComponent);
        comp = fixture.componentInstance;

        comp.comparison = {
            submissionA: { studentLogin: 'studentA' },
            submissionB: { studentLogin: 'studentB' },
        } as PlagiarismComparison<ModelingSubmissionElement>;
        comp.splitControlSubject = splitControlSubject;
    });

    it('checks type of modeling exercise', () => {
        comp.ngOnChanges({
            exercise: { currentValue: modelingExercise } as SimpleChange,
        });

        expect(comp.isModelingExercise).toEqual(true);
        expect(comp.isProgrammingOrTextExercise).toEqual(false);
    });

    it('checks type of text exercise', () => {
        comp.ngOnChanges({
            exercise: { currentValue: textExercise } as SimpleChange,
        });

        expect(comp.isProgrammingOrTextExercise).toEqual(true);
        expect(comp.isModelingExercise).toEqual(false);
    });

    it('should subscribe to the split control subject', () => {
        comp.exercise = textExercise;
        spyOn(splitControlSubject, 'subscribe');

        fixture.detectChanges();
        expect(comp.splitControlSubject.subscribe).toHaveBeenCalled();
    });

    it('should collapse the left pane', () => {
        comp.exercise = textExercise;
        comp.split = ({ collapse } as unknown) as Split.Instance;

        comp.handleSplitControl('left');

        expect(collapse).toHaveBeenCalledWith(1);
    });

    it('should collapse the right pane', () => {
        comp.exercise = textExercise;
        comp.split = ({ collapse } as unknown) as Split.Instance;

        comp.handleSplitControl('right');

        expect(collapse).toHaveBeenCalledWith(0);
    });

    it('should reset the split panes', () => {
        comp.exercise = textExercise;
        comp.split = ({ setSizes } as unknown) as Split.Instance;

        comp.handleSplitControl('even');

        expect(setSizes).toHaveBeenCalledWith([50, 50]);
    });
});
