import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { MockComponent, MockDirective } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

describe('Exam Navigation Bar Component', () => {
    let fixture: ComponentFixture<ExamNavigationBarComponent>;
    let comp: ExamNavigationBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [ExamNavigationBarComponent, MockComponent(ExamTimerComponent), MockDirective(NgbTooltip)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamNavigationBarComponent);
        comp = fixture.componentInstance;

        comp.endDate = moment();
        comp.exercises = [
            {
                id: 0,
                type: ExerciseType.PROGRAMMING,
                studentParticipations: [
                    {
                        submissions: [{ id: 3 } as Submission],
                    } as StudentParticipation,
                ],
            } as Exercise,
            { id: 1, type: ExerciseType.TEXT } as Exercise,
            { id: 2, type: ExerciseType.MODELING } as Exercise,
        ];
    });

    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
    }));

    it('trigger when the exam is about to end', () => {
        spyOn(comp, 'saveExercise');
        spyOn(comp.examAboutToEnd, 'emit');

        comp.triggerExamAboutToEnd();

        expect(comp.saveExercise).toHaveBeenCalled();
        expect(comp.examAboutToEnd.emit).toHaveBeenCalled();
    });

    it('should change the exercise', () => {
        spyOn(comp.onExerciseChanged, 'emit');
        spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex).toEqual(0);

        const exerciseIndex = 1;
        const force = false;

        comp.changeExercise(exerciseIndex, force);

        expect(comp.exerciseIndex).toEqual(exerciseIndex);
        expect(comp.onExerciseChanged.emit).toHaveBeenCalled();
        expect(comp.setExerciseButtonStatus).toHaveBeenCalledWith(exerciseIndex);
    });

    it('should not change the exercise with invalid index', () => {
        spyOn(comp.onExerciseChanged, 'emit');
        spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex).toEqual(0);

        const exerciseIndex = 5;
        const force = false;

        comp.changeExercise(exerciseIndex, force);

        expect(comp.exerciseIndex).toEqual(0);
        expect(comp.onExerciseChanged.emit).not.toHaveBeenCalled();
        expect(comp.setExerciseButtonStatus).not.toHaveBeenCalledWith(exerciseIndex);
    });

    it('should tell the type of the selected programming exercise', () => {
        comp.exerciseIndex = 0;

        expect(comp.isProgrammingExercise()).toBe(true);
    });

    it('should tell the type of the selected text exercise', () => {
        comp.exerciseIndex = 1;

        expect(comp.isProgrammingExercise()).toBe(false);
    });

    it('should tell the type of the selected modeling exercise', () => {
        comp.exerciseIndex = 2;

        expect(comp.isProgrammingExercise()).toBe(false);
    });

    it('save the exercise with changeExercise', () => {
        spyOn(comp, 'changeExercise');
        const changeExercise = true;

        comp.saveExercise(changeExercise);

        expect(comp.changeExercise).toHaveBeenCalled();
    });

    it('save the exercise without changeExercise', () => {
        spyOn(comp, 'changeExercise');
        const changeExercise = false;

        comp.saveExercise(changeExercise);

        expect(comp.changeExercise).not.toHaveBeenCalled();
    });

    it('should hand in the exam early', () => {
        spyOn(comp, 'saveExercise');
        spyOn(comp.onExamHandInEarly, 'emit');

        comp.handInEarly();

        expect(comp.saveExercise).toHaveBeenCalled();
        expect(comp.onExamHandInEarly.emit).toHaveBeenCalled();
    });
});
