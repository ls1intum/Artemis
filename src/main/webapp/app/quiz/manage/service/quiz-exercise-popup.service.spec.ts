import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { QuizExercisePopupService } from './quiz-exercise-popup.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Component } from '@angular/core';

@Component({ template: '' })
class MockModalComponent {}

describe('QuizExercisePopupService', () => {
    setupTestBed({ zoneless: true });

    let service: QuizExercisePopupService;
    let modalService: NgbModal;
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [QuizExercisePopupService, MockProvider(NgbModal), MockProvider(Router)],
        });

        service = TestBed.inject(QuizExercisePopupService);
        modalService = TestBed.inject(NgbModal);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should open modal and set quiz exercise and files', async () => {
        const files = new Map<string, File>();
        files.set('test.png', new File([''], 'test.png'));

        const mockModalRef = {
            componentInstance: {} as Record<string, unknown>,
            result: Promise.resolve('re-evaluate'),
        } as NgbModalRef;

        vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        const result = await service.open(MockModalComponent as unknown as Component, quizExercise, files);

        expect(result).toBe(mockModalRef);
        expect(modalService.open).toHaveBeenCalledWith(MockModalComponent, { size: 'lg', backdrop: 'static' });
        expect(mockModalRef.componentInstance['quizExercise']).toBe(quizExercise);
        expect(mockModalRef.componentInstance['files']).toBe(files);

        // Wait for promise to resolve
        await mockModalRef.result;
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management/' + course.id + '/quiz-exercises']);
    });

    it('should navigate to quiz exercises on re-evaluate result', async () => {
        const files = new Map<string, File>();
        const mockModalRef = {
            componentInstance: {} as Record<string, unknown>,
            result: Promise.resolve('re-evaluate'),
        } as NgbModalRef;

        vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        await service.open(MockModalComponent as unknown as Component, quizExercise, files);

        await mockModalRef.result;
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management/123/quiz-exercises']);
    });

    it('should clear popup outlet on other result', async () => {
        const files = new Map<string, File>();
        const mockModalRef = {
            componentInstance: {} as Record<string, unknown>,
            result: Promise.resolve('other'),
        } as NgbModalRef;

        vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        await service.open(MockModalComponent as unknown as Component, quizExercise, files);

        await mockModalRef.result;
        expect(navigateSpy).toHaveBeenCalledWith([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
    });

    it('should clear popup outlet on modal dismiss', async () => {
        const files = new Map<string, File>();
        const mockModalRef = {
            componentInstance: {} as Record<string, unknown>,
            result: Promise.reject('dismiss'),
        } as NgbModalRef;

        vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        await service.open(MockModalComponent as unknown as Component, quizExercise, files);

        // Wait for rejection to be handled
        await new Promise((resolve) => setTimeout(resolve, 0));
        expect(navigateSpy).toHaveBeenCalledWith([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
    });

    it('should return existing modal ref if already open', async () => {
        const files = new Map<string, File>();
        const mockModalRef = {
            componentInstance: {} as Record<string, unknown>,
            result: new Promise(() => {}), // Never resolves
        } as NgbModalRef;

        vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

        const firstResult = await service.open(MockModalComponent as unknown as Component, quizExercise, files);
        const secondResult = await service.open(MockModalComponent as unknown as Component, quizExercise, files);

        expect(firstResult).toBe(secondResult);
        expect(modalService.open).toHaveBeenCalledOnce();
    });
});
