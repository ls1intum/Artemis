import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyDetailLectureUnitsComponent } from 'app/course/competencies/components/competency-detail-lecture-units/competency-detail-lecture-units.component';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Competency, CourseCompetencyType } from 'app/entities/competency.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { of } from 'rxjs';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';

describe('CompetencyDetailLectureUnitsComponent', () => {
    let component: CompetencyDetailLectureUnitsComponent;
    let fixture: ComponentFixture<CompetencyDetailLectureUnitsComponent>;
    let lectureUnitService: LectureUnitService;
    let alertService: AlertService;

    const competencyId = 1;

    const competency = <Competency>{
        type: CourseCompetencyType.COMPETENCY,
        id: competencyId,
        title: 'Competency 1',
        description: '## Descriptiuon\nHier steht viel über eine Kompetenz das kann auch viel Text sein',
        taxonomy: 'UNDERSTAND',
        masteryThreshold: 100,
        optional: false,
        lectureUnits: [
            {
                id: 7,
                name: 'Text Exercise 1',
                lecture: {
                    id: 2,
                    title: 'Lecture 1',
                    visibleToStudents: true,
                },
                content: 'Text Exercise',
                type: LectureUnitType.TEXT,
                completed: true,
                visibleToStudents: true,
            },
            {
                id: 9,
                name: 'Text Lecture',
                type: LectureUnitType.TEXT,
                completed: false,
                visibleToStudents: true,
            },
        ],
        exercises: [
            {
                id: 1,
                title: 'Exercise 1',
                type: ExerciseType.TEXT,
                completed: false,
            },
        ],
        userProgress: [
            {
                progress: 50.0,
                confidence: 1.0,
                confidenceReason: 'NO_REASON',
            },
        ],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyDetailLectureUnitsComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: LectureUnitService,
                    useValue: {
                        setCompletion: jest.fn(),
                    },
                },
                { provide: ScienceService, useClass: MockScienceService },
            ],
        }).compileComponents();

        lectureUnitService = TestBed.inject(LectureUnitService);
        alertService = TestBed.inject(AlertService);

        fixture = TestBed.createComponent(CompetencyDetailLectureUnitsComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('competency', competency);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.competency()).toEqual(competency);
    });

    it('should set units correctly', () => {
        fixture.detectChanges();
        expect(component.units()).toEqual([...competency.lectureUnits!, ...competency.exercises!.map((exercise) => <ExerciseUnit>{ id: exercise.id, exercise: exercise })]);
    });

    it('should set progress correctly', async () => {
        const setCompletionSpy = jest.spyOn(lectureUnitService, 'setCompletion').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
        const onLectureUnitCompletionSpy = jest.spyOn(component.onLectureUnitCompletion, 'emit');

        const lectureUnit = competency.lectureUnits![0];
        const lectureUnitCompletionEvent = <LectureUnitCompletionEvent>{ lectureUnit, completed: true };

        await component.setLearningObjectCompletion(lectureUnitCompletionEvent);

        expect(setCompletionSpy).toHaveBeenCalledWith(lectureUnit.id!, lectureUnit.lecture!.id!, true);
        expect(onLectureUnitCompletionSpy).toHaveBeenCalledOnce();
    });

    it('should show alert on error', async () => {
        const alertErrorSpy = jest.spyOn(alertService, 'addAlert');

        const lectureUnit = competency.lectureUnits![0];
        const lectureUnitCompletionEvent = <LectureUnitCompletionEvent>{ lectureUnit, completed: true };

        await component.setLearningObjectCompletion(lectureUnitCompletionEvent);

        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });
});
