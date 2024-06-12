import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LearningPathStudentNavComponent } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { LearningObjectType, LearningPathNavigationDTO } from 'app/entities/competency/learning-path.model';
import { By } from '@angular/platform-browser';
import { LearningPathStudentNavOverviewComponent } from 'app/course/learning-paths/components/learning-path-student-nav-overview/learning-path-student-nav-overview.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';

describe('LearningPathStudentNavComponent', () => {
    let component: LearningPathStudentNavComponent;
    let fixture: ComponentFixture<LearningPathStudentNavComponent>;
    let learningPathApiService: LearningPathApiService;
    let getLearningPathNavigationSpy: jest.SpyInstance;

    const navigationDto: LearningPathNavigationDTO = {
        predecessorLearningObject: {
            id: 1,
            name: 'Exercise 1',
            type: LearningObjectType.EXERCISE,
            completed: true,
        },
        currentLearningObject: {
            id: 2,
            name: 'Lecture 2',
            type: LearningObjectType.LECTURE,
            completed: false,
        },
        successorLearningObject: {
            id: 3,
            name: 'Exercise 3',
            type: LearningObjectType.EXERCISE,
            completed: false,
        },
        progress: 50,
    };

    const learningPathId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathStudentNavComponent],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        })
            .overrideComponent(LearningPathStudentNavComponent, {
                add: {
                    imports: [LearningPathStudentNavOverviewComponent],
                },
            })
            .compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        getLearningPathNavigationSpy = jest.spyOn(learningPathApiService, 'getLearningPathNavigation').mockResolvedValue(navigationDto);

        fixture = TestBed.createComponent(LearningPathStudentNavComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', async () => {
        fixture.detectChanges();

        expect(component).toBeTruthy();
        expect(component.learningPathId()).toBe(learningPathId);
        expect(component.showNavigationOverview()).toBeFalse();
    });

    it('should show progress bar percentage', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const progressBar = fixture.debugElement.query(By.css('.progress-bar'));
        expect(progressBar.nativeElement.style.width).toBe('50%');
    });

    it('should navigation with back and previous button', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.predecessorLearningObject()).toEqual(navigationDto.predecessorLearningObject);
        expect(component.currentLearningObject()).toEqual(navigationDto.currentLearningObject);
        expect(component.successorLearningObject()).toEqual(navigationDto.successorLearningObject);
        const previousButton = fixture.debugElement.query(By.css('#previous-button'));
        expect(previousButton).toBeTruthy();
        const nextButton = fixture.debugElement.query(By.css('#next-button'));
        expect(nextButton).toBeTruthy();
    });

    it('should show navigation with only previous button', async () => {
        const navigationDto = {
            predecessorLearningObject: {
                id: 1,
                name: 'Exercise',
                type: LearningObjectType.EXERCISE,
                completed: true,
            },
            currentLearningObject: {
                id: 2,
                name: 'Lecture',
                type: LearningObjectType.LECTURE,
                completed: false,
            },
            progress: 95,
        };
        getLearningPathNavigationSpy.mockResolvedValue(navigationDto);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.predecessorLearningObject()).toEqual(navigationDto.predecessorLearningObject);
        expect(component.currentLearningObject()).toEqual(navigationDto.currentLearningObject);
        expect(component.successorLearningObject()).toBeUndefined();

        const previousButton = fixture.debugElement.query(By.css('#previous-button'));
        expect(previousButton).toBeTruthy();

        const nextButton = fixture.debugElement.query(By.css('#next-button'));
        expect(nextButton).toBeFalsy();
    });

    it('should show navigation with only next button', async () => {
        const navigationDto = {
            currentLearningObject: {
                id: 2,
                name: 'Lecture',
                type: LearningObjectType.LECTURE,
                completed: false,
            },
            successorLearningObject: {
                id: 3,
                name: 'Exercise',
                type: LearningObjectType.EXERCISE,
                completed: false,
            },
            progress: 0,
        };
        getLearningPathNavigationSpy.mockResolvedValue(navigationDto);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.predecessorLearningObject()).toBeUndefined();
        expect(component.currentLearningObject()).toEqual(navigationDto.currentLearningObject);
        expect(component.successorLearningObject()).toEqual(navigationDto.successorLearningObject);

        const previousButton = fixture.debugElement.query(By.css('#previous-button'));
        expect(previousButton).toBeFalsy();

        const nextButton = fixture.debugElement.query(By.css('#next-button'));
        expect(nextButton).toBeTruthy();
    });

    it('should show navigation overview on click', async () => {
        const setShowNavigationOverviewSpy = jest.spyOn(component, 'setShowNavigationOverview');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const navOverviewButton = fixture.debugElement.query(By.css('#navigation-overview'));
        navOverviewButton.nativeElement.click();
        fixture.detectChanges();
        const navOverview = fixture.debugElement.query(By.directive(LearningPathStudentNavOverviewComponent));
        expect(navOverview).toBeTruthy();
        expect(setShowNavigationOverviewSpy).toHaveBeenCalledWith(true);
        expect(component.showNavigationOverview()).toBeTrue();
    });

    it('should call select learning object on previous click', async () => {
        const selectLearningObjectSpy = jest.spyOn(component, 'selectLearningObject');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const previousButton = fixture.debugElement.query(By.css('#previous-button'));
        previousButton.nativeElement.click();

        fixture.detectChanges();

        expect(getLearningPathNavigationSpy).toHaveBeenCalledTimes(2);
        expect(selectLearningObjectSpy).toHaveBeenCalledWith(navigationDto.predecessorLearningObject);
    });
});
