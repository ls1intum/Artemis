import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

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
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), GradingSystemService],
        })
            .overrideTemplate(PresentationScoreComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(PresentationScoreComponent);
                component = componentFixture.componentInstance;
                TestBed.inject(GradingSystemService);
                TestBed.inject(HttpTestingController);
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
