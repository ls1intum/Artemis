import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';

describe('ProgrammingExamDiffComponent', () => {
    let component: ProgrammingExerciseExamDiffComponent;
    let fixture: ComponentFixture<ProgrammingExerciseExamDiffComponent>;
    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ProgrammingExerciseExamDiffComponent],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseExamDiffComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
