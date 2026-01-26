import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/atlas/overview/learning-path-navigation.service';
import { AlertService } from 'app/shared/service/alert.service';
import { LearningObjectType, LearningPathNavigationDTO } from 'app/atlas/shared/entities/learning-path.model';
import { provideHttpClient } from '@angular/common/http';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathNavigationService', () => {
    setupTestBed({ zoneless: true });
    let learningPathNavigationService: LearningPathNavigationService;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;

    const learningPathId = 1;

    const learningPathNavigationDto = {
        predecessorLearningObject: {
            id: 1,
            name: 'Lecture 1',
            completed: true,
            type: LearningObjectType.LECTURE,
        },
        currentLearningObject: {
            id: 2,
            name: 'Exercise 1',
            completed: false,
            type: LearningObjectType.EXERCISE,
        },
        successorLearningObject: {
            id: 3,
            name: 'Lecture 2',
            completed: false,
            type: LearningObjectType.LECTURE,
        },
        progress: 40,
    } as LearningPathNavigationDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                LearningPathApiService,
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
            ],
        });

        learningPathApiService = TestBed.inject(LearningPathApiService);
        learningPathNavigationService = TestBed.inject(LearningPathNavigationService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load initial learning path navigation', async () => {
        const loadLearningPathNavigationSpy = vi.spyOn(learningPathApiService, 'getLearningPathNavigation').mockResolvedValue(learningPathNavigationDto);

        await learningPathNavigationService.loadLearningPathNavigation(learningPathId);

        expect(learningPathNavigationService.learningPathNavigation()).toEqual(learningPathNavigationDto);
        expect(learningPathNavigationService.currentLearningObject()).toEqual(learningPathNavigationDto.currentLearningObject);
        expect(learningPathNavigationService.isCurrentLearningObjectCompleted()).toEqual(learningPathNavigationDto.currentLearningObject!.completed);
        expect(loadLearningPathNavigationSpy).toHaveBeenCalledWith(learningPathId);
    });

    it('should load relative learning path navigation', async () => {
        const selectedLearningObject = learningPathNavigationDto.currentLearningObject;
        const loadRelativeLearningPathNavigationSpy = vi.spyOn(learningPathApiService, 'getRelativeLearningPathNavigation').mockResolvedValue(learningPathNavigationDto);

        await learningPathNavigationService.loadRelativeLearningPathNavigation(learningPathId, selectedLearningObject!);

        expect(learningPathNavigationService.learningPathNavigation()).toEqual(learningPathNavigationDto);
        expect(learningPathNavigationService.currentLearningObject()).toEqual(learningPathNavigationDto.currentLearningObject);
        expect(learningPathNavigationService.isCurrentLearningObjectCompleted()).toEqual(learningPathNavigationDto.currentLearningObject!.completed);
        expect(loadRelativeLearningPathNavigationSpy).toHaveBeenCalledWith(
            learningPathId,
            selectedLearningObject!.id,
            selectedLearningObject!.type,
            selectedLearningObject!.competencyId,
        );
    });

    it('should complete learning path', async () => {
        const navigationDto = {
            predecessorLearningObject: {
                id: 2,
                name: 'Exercise 1',
                completed: false,
                type: LearningObjectType.EXERCISE,
            },
            progress: 90,
        } as LearningPathNavigationDTO;

        vi.spyOn(learningPathApiService, 'getLearningPathNavigation').mockResolvedValue(navigationDto);
        const completeLearningPathSpy = vi.spyOn(learningPathNavigationService, 'completeLearningPath');

        await learningPathNavigationService.loadLearningPathNavigation(learningPathId);

        learningPathNavigationService.completeLearningPath();

        expect(learningPathNavigationService.learningPathNavigation()).toEqual({
            predecessorLearningObject: navigationDto.currentLearningObject,
            currentLearningObject: undefined,
            successorLearningObject: undefined,
            progress: 100,
        });
        expect(completeLearningPathSpy).toHaveBeenCalled();
    });

    it('should call alert service on learning path loading fail', async () => {
        vi.spyOn(learningPathApiService, 'getLearningPathNavigation').mockRejectedValue(Error('Server error'));
        const alertServiceErrorSpy = vi.spyOn(alertService, 'error');

        await learningPathNavigationService.loadLearningPathNavigation(learningPathId);

        expect(alertServiceErrorSpy).toHaveBeenCalled();
    });

    it('should call alert service on relative learning path loading fail', async () => {
        vi.spyOn(learningPathApiService, 'getRelativeLearningPathNavigation').mockRejectedValue(Error('Server error'));
        const alertServiceErrorSpy = vi.spyOn(alertService, 'error');

        await learningPathNavigationService.loadRelativeLearningPathNavigation(learningPathId, learningPathNavigationDto.currentLearningObject!);

        expect(alertServiceErrorSpy).toHaveBeenCalled();
    });

    it('should set current learning object completion to true', () => {
        learningPathNavigationService.setCurrentLearningObjectCompletion(true);
        expect(learningPathNavigationService.isCurrentLearningObjectCompleted()).toBeTrue();
    });

    it('should set current learning object completion to false', () => {
        learningPathNavigationService.setCurrentLearningObjectCompletion(false);
        expect(learningPathNavigationService.isCurrentLearningObjectCompleted()).toBeFalse();
    });
});
