import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LearningPathStudentNavComponent } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { of } from 'rxjs';
import { LearningObjectType, LearningPathNavigationDto } from 'app/entities/competency/learning-path.model';
import { By } from '@angular/platform-browser';
import { LearningPathStudentNavOverviewComponent } from 'app/course/learning-paths/components/learning-path-student-nav-overview/learning-path-student-nav-overview.component';

describe('LearningPathStudentNavComponent', () => {
    let component: LearningPathStudentNavComponent;
    let fixture: ComponentFixture<LearningPathStudentNavComponent>;
    let learningPathService: LearningPathService;
    let getLearningPathNavigationSpy: jest.SpyInstance;

    let navigationDto: LearningPathNavigationDto = {
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
        successorLearningObject: {
            id: 3,
            name: 'Exercise',
            type: LearningObjectType.EXERCISE,
            completed: false,
        },
        progress: 50,
    };

    const learningPathId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathStudentNavComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useValue: jest.fn() }],
        })
            .overrideComponent(LearningPathStudentNavComponent, {
                add: {
                    imports: [LearningPathStudentNavOverviewComponent],
                },
            })
            .compileComponents();

        learningPathService = TestBed.inject(LearningPathService);
        getLearningPathNavigationSpy = jest.spyOn(learningPathService, 'getLearningPathNavigation');
        getLearningPathNavigationSpy.mockReturnValue(of(new HttpResponse({ body: navigationDto })));

        fixture = TestBed.createComponent(LearningPathStudentNavComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeTruthy();
        expect(component.learningPathId()).toBe(learningPathId);
        expect(component.showNavigationOverview()).toBeFalse();
    });

    it('should show progress bar percentage', fakeAsync(() => {
        getLearningPathNavigationSpy.mockReturnValue(of(new HttpResponse({ body: navigationDto })));

        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(getLearningPathNavigationSpy).toHaveBeenCalledOnce();
        const progressBar = fixture.debugElement.query(By.css('.progress-bar'));
        expect(progressBar.nativeElement.style.width).toBe('50%');
    }));

    it('should navigation with back and previous button', fakeAsync(() => {
        getLearningPathNavigationSpy.mockReturnValue(of(new HttpResponse({ body: navigationDto })));

        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(getLearningPathNavigationSpy).toHaveBeenCalledOnce();
        expect(component.predecessorLearningObject()).toEqual(navigationDto.predecessorLearningObject);
        expect(component.currentLearningObject()).toEqual(navigationDto.currentLearningObject);
        expect(component.successorLearningObject()).toEqual(navigationDto.successorLearningObject);
        const previousButton = fixture.debugElement.query(By.css('#previous-button'));
        expect(previousButton).toBeTruthy();
        const nextButton = fixture.debugElement.query(By.css('#next-button'));
        expect(nextButton).toBeTruthy();
    }));

    it('should navigation with only previous button', fakeAsync(() => {
        navigationDto = {
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
        getLearningPathNavigationSpy.mockReturnValue(of(new HttpResponse({ body: navigationDto })));

        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(getLearningPathNavigationSpy).toHaveBeenCalledOnce();
        expect(component.predecessorLearningObject()).toEqual(navigationDto.predecessorLearningObject);
        expect(component.currentLearningObject()).toEqual(navigationDto.currentLearningObject);
        expect(component.successorLearningObject()).toBeUndefined();
        const previousButton = fixture.debugElement.query(By.css('#previous-button'));
        expect(previousButton).toBeTruthy();
        const nextButton = fixture.debugElement.query(By.css('#next-button'));
        expect(nextButton).toBeFalsy();
    }));

    it('should navigation with only next button', fakeAsync(() => {
        navigationDto = {
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
        getLearningPathNavigationSpy.mockReturnValue(of(new HttpResponse({ body: navigationDto })));

        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(getLearningPathNavigationSpy).toHaveBeenCalledOnce();
        expect(component.predecessorLearningObject()).toBeUndefined();
        expect(component.currentLearningObject()).toEqual(navigationDto.currentLearningObject);
        expect(component.successorLearningObject()).toEqual(navigationDto.successorLearningObject);
        const previousButton = fixture.debugElement.query(By.css('#previous-button'));
        expect(previousButton).toBeFalsy();
        const nextButton = fixture.debugElement.query(By.css('#next-button'));
        expect(nextButton).toBeTruthy();
    }));

    it('should show navigation overview', fakeAsync(() => {
        getLearningPathNavigationSpy.mockReturnValue(of(new HttpResponse({ body: navigationDto })));
        const setShowNavigationOverviewSpy = jest.spyOn(component, 'setShowNavigationOverview');

        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        const navOverviewButton = fixture.debugElement.query(By.css('#navigation-overview'));
        navOverviewButton.nativeElement.click();
        fixture.detectChanges();
        const navOverview = fixture.debugElement.query(By.css('jhi-learning-path-student-nav-overview'));
        expect(navOverview).toBeTruthy();
        expect(setShowNavigationOverviewSpy).toHaveBeenCalledWith(true);
        expect(component.showNavigationOverview()).toBeTrue();
    }));

    it('should call select learning object on previous click', fakeAsync(() => {
        getLearningPathNavigationSpy.mockReturnValue(of(new HttpResponse({ body: navigationDto })));
        const selectLearningObjectSpy = jest.spyOn(component, 'selectLearningObject');

        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        const previousButton = fixture.debugElement.nativeElement.querySelector('#previous-button');
        previousButton.click();
        fixture.detectChanges();
        expect(selectLearningObjectSpy).toHaveBeenCalledWith(navigationDto.predecessorLearningObject);
        expect(getLearningPathNavigationSpy).toHaveBeenCalledTimes(2);
    }));
});
