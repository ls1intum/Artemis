import { Location } from '@angular/common';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoalsPopoverComponent } from 'app/course/learning-goals/learning-goals-popover/learning-goals-popover.component';
import { By } from '@angular/platform-browser';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { Component } from '@angular/core';

@Component({
    template: '',
})
class DummyStatisticsComponent {}

@Component({
    template: '',
})
class DummyManagementComponent {}

describe('LearningGoalPopoverComponent', () => {
    let learningGoalPopoverComponentFixture: ComponentFixture<LearningGoalsPopoverComponent>;
    let learningGoalPopoverComponent: LearningGoalsPopoverComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                NgbPopoverModule,
                RouterTestingModule.withRoutes([
                    { path: 'courses/:courseId/statistics', component: DummyStatisticsComponent },
                    { path: 'course-management/:courseId/goal-management', component: DummyManagementComponent },
                ]),
            ],
            declarations: [LearningGoalsPopoverComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), DummyStatisticsComponent, DummyManagementComponent],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalPopoverComponentFixture = TestBed.createComponent(LearningGoalsPopoverComponent);
                learningGoalPopoverComponent = learningGoalPopoverComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        learningGoalPopoverComponentFixture.detectChanges();
        expect(learningGoalPopoverComponent).toBeDefined();
    });

    it('should navigate to course statistics', fakeAsync(() => {
        const location: Location = TestBed.inject(Location);
        learningGoalPopoverComponent.navigateTo = 'courseStatistics';
        learningGoalPopoverComponent.learningGoals = [new LearningGoal()];
        learningGoalPopoverComponent.courseId = 1;
        learningGoalPopoverComponentFixture.detectChanges();
        const popoverButton = learningGoalPopoverComponentFixture.debugElement.nativeElement.querySelector('button');
        popoverButton.click();
        tick();
        const anchor = learningGoalPopoverComponentFixture.debugElement.query(By.css('a')).nativeElement;
        anchor.click();
        tick();
        expect(location.path()).toEqual('/courses/1/statistics');
    }));

    it('should navigate to learning goal management', fakeAsync(() => {
        const location: Location = TestBed.inject(Location);
        learningGoalPopoverComponent.navigateTo = 'learningGoalManagement';
        learningGoalPopoverComponent.learningGoals = [new LearningGoal()];
        learningGoalPopoverComponent.courseId = 1;
        learningGoalPopoverComponentFixture.detectChanges();
        const popoverButton = learningGoalPopoverComponentFixture.debugElement.nativeElement.querySelector('button');
        popoverButton.click();
        tick();
        const anchor = learningGoalPopoverComponentFixture.debugElement.query(By.css('a')).nativeElement;
        anchor.click();
        tick();
        expect(location.path()).toEqual('/course-management/1/goal-management');
    }));
});
