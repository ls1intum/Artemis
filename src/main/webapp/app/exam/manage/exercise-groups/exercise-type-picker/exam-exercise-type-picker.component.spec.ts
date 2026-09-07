import { TumUiDialogComponent } from '@tumaet/ui-angular';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { afterEach, vi } from 'vitest';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamExerciseTypePickerComponent } from 'app/exam/manage/exercise-groups/exercise-type-picker/exam-exercise-type-picker.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ExamExerciseTypePickerComponent', () => {
    let fixture: ComponentFixture<ExamExerciseTypePickerComponent>;
    let component: ExamExerciseTypePickerComponent;
    let router: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamExerciseTypePickerComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .overrideComponent(ExamExerciseTypePickerComponent, {
                set: { imports: [TumUiDialogComponent, FaIconComponent, ArtemisTranslatePipe, TranslateDirective] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExamExerciseTypePickerComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('examId', 2);
        fixture.componentRef.setInput('groupId', 3);
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    it('shows a card for every exercise type by default', () => {
        expect(component['typeCards']().map((card) => card.type)).toEqual([
            ExerciseType.PROGRAMMING,
            ExerciseType.QUIZ,
            ExerciseType.MODELING,
            ExerciseType.TEXT,
            ExerciseType.FILE_UPLOAD,
        ]);
    });

    it('hides disabled exercise types', () => {
        fixture.componentRef.setInput('disabledExerciseTypes', [ExerciseType.MODELING, ExerciseType.FILE_UPLOAD]);
        fixture.detectChanges();

        expect(component['typeCards']().map((card) => card.type)).toEqual([ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.TEXT]);
    });

    it('on the create tab, navigates to the create route for the chosen type and closes', () => {
        vi.spyOn(router, 'navigate');
        component['setActiveTab']('create');

        component['onCardClick'](ExerciseType.QUIZ);

        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 1, 'exams', 2, 'exercise-groups', 3, 'quiz-exercises', 'new']);
        expect(component.visible()).toBe(false);
    });

    it('on the import tab, emits importRequested for the chosen type and closes without navigating', () => {
        vi.spyOn(router, 'navigate');
        component['setActiveTab']('import');
        const requested: ExerciseType[] = [];
        component.importRequested.subscribe((type) => requested.push(type));

        component['onCardClick'](ExerciseType.TEXT);

        expect(requested).toEqual([ExerciseType.TEXT]);
        expect(router.navigate).not.toHaveBeenCalled();
        expect(component.visible()).toBe(false);
    });

    it('re-applies the opener-chosen mode as the active tab whenever the dialog opens', () => {
        fixture.componentRef.setInput('mode', 'import');
        component['setActiveTab']('create'); // simulate a left-over tab selection from a previous open
        component.visible.set(true);
        fixture.detectChanges();

        expect(component['activeTab']()).toBe('import');
    });
});
