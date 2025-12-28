import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { QuizExerciseCreateButtonsComponent } from 'app/quiz/manage/create-buttons/quiz-exercise-create-buttons.component';
import { provideHttpClient } from '@angular/common/http';

describe('QuizExercise Create Buttons Component', () => {
    let comp: QuizExerciseCreateButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseCreateButtonsComponent>;
    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizExerciseCreateButtonsComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });
});
