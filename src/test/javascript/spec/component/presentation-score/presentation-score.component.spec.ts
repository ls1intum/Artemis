import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';

describe('PresentationScoreComponent', () => {
    let component: PresentationScoreComponent;
    let componentFixture: ComponentFixture<PresentationScoreComponent>;

    const courseWithPresentationScore = {
        id: 1,
        title: 'Presentation Score',
        presentationScore: 2,
    } as Course;

    const courseWithoutPresentationScore = {
        id: 2,
        title: 'No Presentation Score',
        presentationScore: 0,
    } as Course;

    const exercise1 = {
        id: 1,
        title: 'Exercise 1',
        course: courseWithPresentationScore,
        isAtLeastInstructor: true,
    } as Exercise;

    const exercise2 = {
        id: 2,
        title: 'Exercise 2',
        course: courseWithoutPresentationScore,
        isAtLeastInstructor: true,
    } as Exercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [PresentationScoreComponent],
        })
            .overrideTemplate(PresentationScoreComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(PresentationScoreComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should show the presentation score checkbox', () => {
        component.exercise = exercise1;
        componentFixture.detectChanges();
        expect(component.showPresentationScoreCheckbox()).toBeTrue();
    });

    it('should hide the presentation score checkbox', () => {
        component.exercise = exercise2;
        componentFixture.detectChanges();
        expect(component.showPresentationScoreCheckbox()).toBeFalse();
    });
});
