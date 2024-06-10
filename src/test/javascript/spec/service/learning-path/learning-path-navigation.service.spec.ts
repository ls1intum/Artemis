import { TestBed } from '@angular/core/testing';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { AlertService } from 'app/core/util/alert.service';
import { LearningObjectType, LearningPathNavigationDto } from 'app/entities/competency/learning-path.model';
import { provideHttpClient } from '@angular/common/http';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';

describe('LearningPathNavigationService', () => {
    let learningPathNavigationService: LearningPathNavigationService;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;

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
    } as LearningPathNavigationDto;

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
        jest.restoreAllMocks();
    });

    it('should load initial learning path navigation', async () => {
        const learningPathId = 1;
        const loadLearningPathNavigationSpy = jest.spyOn(learningPathApiService, 'getLearningPathNavigation').mockResolvedValue(learningPathNavigationDto);

        await learningPathNavigationService.loadInitialLearningPathNavigation(learningPathId);

        expect(learningPathNavigationService.learningPathNavigation()).toEqual(learningPathNavigationDto);
        expect(learningPathNavigationService.currentLearningObject()).toEqual(learningPathNavigationDto.currentLearningObject);
        expect(learningPathNavigationService.isCurrentLearningObjectCompleted()).toEqual(learningPathNavigationDto.currentLearningObject.completed);
        expect(loadLearningPathNavigationSpy).toHaveBeenCalledWith(learningPathId, undefined, undefined);
    });

    it('should load relative learning path navigation', async () => {
        const learningPathId = 1;
        const selectedLearningObject = learningPathNavigationDto.currentLearningObject;
        const loadLearningPathNavigationSpy = jest.spyOn(learningPathApiService, 'getLearningPathNavigation').mockResolvedValue(learningPathNavigationDto);

        await learningPathNavigationService.loadRelativeLearningPathNavigation(learningPathId, selectedLearningObject);

        expect(learningPathNavigationService.learningPathNavigation()).toEqual(learningPathNavigationDto);
        expect(learningPathNavigationService.currentLearningObject()).toEqual(learningPathNavigationDto.currentLearningObject);
        expect(learningPathNavigationService.isCurrentLearningObjectCompleted()).toEqual(learningPathNavigationDto.currentLearningObject.completed);
        expect(loadLearningPathNavigationSpy).toHaveBeenCalledWith(learningPathId, selectedLearningObject.id, selectedLearningObject.type);
    });

    it('should call alert service on learning path loading fail', async () => {
        const learningPathId = 1;

        jest.spyOn(learningPathApiService, 'getLearningPathNavigation').mockRejectedValue(Error('Server error'));
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        await learningPathNavigationService.loadInitialLearningPathNavigation(learningPathId);

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
