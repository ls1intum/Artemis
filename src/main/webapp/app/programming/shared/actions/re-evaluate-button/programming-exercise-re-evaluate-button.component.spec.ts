import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/programming/shared/actions/re-evaluate-button/programming-exercise-re-evaluate-button.component';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';

describe('ProgrammingExercise Re-Evaluate Button Component', () => {
    setupTestBed({ zoneless: true });

    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    programmingExercise.title = 'Exercise 1';

    let fixture: ComponentFixture<ProgrammingExerciseReEvaluateButtonComponent>;
    let gradingService: ProgrammingExerciseGradingService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LocalStorageService, SessionStorageService, MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseReEvaluateButtonComponent);
        gradingService = TestBed.inject(ProgrammingExerciseGradingService);

        fixture.componentRef.setInput('exercise', programmingExercise);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should reEvaluate exercise', () => {
        vi.spyOn(gradingService, 'reEvaluate');

        const button = fixture.debugElement.nativeElement.querySelector('#re-evaluate-button button');
        button.click();

        expect(gradingService.reEvaluate).toHaveBeenCalledOnce();
        expect(gradingService.reEvaluate).toHaveBeenCalledWith(programmingExercise.id);
    });
});
