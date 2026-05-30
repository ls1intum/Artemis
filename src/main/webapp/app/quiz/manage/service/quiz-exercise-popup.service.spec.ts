import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Router } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { Subject } from 'rxjs';
import { QuizExercisePopupService } from './quiz-exercise-popup.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Component } from '@angular/core';

@Component({ template: '' })
class MockModalComponent {}

describe('QuizExercisePopupService', () => {
    setupTestBed({ zoneless: true });

    let service: QuizExercisePopupService;
    let dialogService: DialogService;
    let router: Router;

    const course = { id: 123 } as Course;
    const quizExercise: QuizExercise = {
        id: 456,
        title: 'Test Quiz',
        course: course,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    /** Creates a dialog-ref mock backed by a controllable onClose subject. */
    const createDialogRefMock = () => {
        const onClose = new Subject<unknown>();
        return { ref: { onClose } as DynamicDialogRef, onClose };
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [QuizExercisePopupService, MockProvider(DialogService), MockProvider(Router)],
        });

        service = TestBed.inject(QuizExercisePopupService);
        dialogService = TestBed.inject(DialogService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should open the dialog and pass the quiz exercise and files via data', async () => {
        const files = new Map<string, File>();
        files.set('test.png', new File([''], 'test.png'));

        const { ref, onClose } = createDialogRefMock();
        const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(ref);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        const result = await service.open(MockModalComponent, quizExercise, files);

        expect(result).toBe(ref);
        expect(openSpy).toHaveBeenCalledWith(MockModalComponent, expect.objectContaining({ data: { quizExercise, files } }));

        // re-evaluate result navigates to the quiz exercises overview
        onClose.next('re-evaluate');
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management/' + course.id + '/quiz-exercises']);
    });

    it('should navigate to quiz exercises on re-evaluate result', async () => {
        const files = new Map<string, File>();
        const { ref, onClose } = createDialogRefMock();
        vi.spyOn(dialogService, 'open').mockReturnValue(ref);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        await service.open(MockModalComponent, quizExercise, files);

        onClose.next('re-evaluate');
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management/123/quiz-exercises']);
    });

    it('should clear popup outlet on other result', async () => {
        const files = new Map<string, File>();
        const { ref, onClose } = createDialogRefMock();
        vi.spyOn(dialogService, 'open').mockReturnValue(ref);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        await service.open(MockModalComponent, quizExercise, files);

        onClose.next('other');
        expect(navigateSpy).toHaveBeenCalledWith([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
    });

    it('should clear popup outlet on dialog dismiss', async () => {
        const files = new Map<string, File>();
        const { ref, onClose } = createDialogRefMock();
        vi.spyOn(dialogService, 'open').mockReturnValue(ref);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        await service.open(MockModalComponent, quizExercise, files);

        onClose.next(undefined);
        expect(navigateSpy).toHaveBeenCalledWith([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
    });

    it('should return existing dialog ref if already open', async () => {
        const files = new Map<string, File>();
        const { ref } = createDialogRefMock();

        const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(ref);

        const firstResult = await service.open(MockModalComponent, quizExercise, files);
        const secondResult = await service.open(MockModalComponent, quizExercise, files);

        expect(firstResult).toBe(secondResult);
        expect(openSpy).toHaveBeenCalledOnce();
    });
});
