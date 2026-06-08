import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseCreateButtonsComponent } from 'app/exercise/exercise-create-buttons/exercise-create-buttons.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Exercise Manage Buttons Component', () => {
    setupTestBed({ zoneless: true });

    let comp: ExerciseCreateButtonsComponent;
    let fixture: ComponentFixture<ExerciseCreateButtonsComponent>;

    const course = { id: 123 } as Course;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(ExerciseCreateButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseCreateButtonsComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });
});
