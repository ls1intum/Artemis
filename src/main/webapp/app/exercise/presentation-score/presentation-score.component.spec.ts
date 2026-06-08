import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

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

const exerciseWithPresentationScore = {
    id: 1,
    title: 'Exercise 1',
    course: courseWithPresentationScore,
    isAtLeastInstructor: true,
} as Exercise;

const exerciseWithoutPresentationScore = {
    id: 2,
    title: 'Exercise 2',
    course: courseWithoutPresentationScore,
    isAtLeastInstructor: true,
} as Exercise;

describe('PresentationScoreComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PresentationScoreComponent;
    let fixture: ComponentFixture<PresentationScoreComponent>;
    let gradingService: GradingService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationScoreComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationScoreComponent);
        component = fixture.componentInstance;
        gradingService = TestBed.inject(GradingService);
        vi.spyOn(gradingService, 'findGradeStepsForCourse').mockReturnValue(of(new HttpResponse({ body: { presentationsNumber: 0 } as GradeStepsDTO })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show the presentation score checkbox', () => {
        fixture.componentRef.setInput('exercise', exerciseWithPresentationScore);
        fixture.detectChanges();

        expect(component.showPresentationScoreCheckbox()).toBe(true);
    });

    it('should hide the presentation score checkbox', () => {
        fixture.componentRef.setInput('exercise', exerciseWithoutPresentationScore);
        fixture.detectChanges();

        expect(component.showPresentationScoreCheckbox()).toBe(false);
    });

    it('should update the exercise presentation score flag from the checkbox', async () => {
        const exercise = {
            id: 3,
            title: 'Exercise 3',
            course: courseWithPresentationScore,
            isAtLeastInstructor: true,
            presentationScoreEnabled: false,
        } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const checkbox = fixture.nativeElement.querySelector('#field_presentationScoreEnabled') as HTMLInputElement;
        checkbox.checked = true;
        checkbox.dispatchEvent(new Event('change'));
        fixture.detectChanges();

        expect(exercise.presentationScoreEnabled).toBe(true);
    });
});
