import { LearningPathExerciseComponent } from 'app/atlas/overview/learning-path-exercise/learning-path-exercise.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('LearningPathExerciseComponent', () => {
    let component: LearningPathExerciseComponent;
    let fixture: ComponentFixture<LearningPathExerciseComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathExerciseComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(LearningPathExerciseComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('exerciseId', 1);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toBe(1);
        expect(component.exerciseId()).toBe(1);
    });
});
