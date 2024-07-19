import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';

import { ExerciseHeadersInformationComponent } from 'app/exercises/shared/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { MockProvider } from 'ng-mocks';
import { InformationBoxComponent } from 'app/shared/information-box/information-box.component';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from '../../../../../../../main/webapp/app/shared/pipes/artemis-date.pipe';

describe('ExerciseHeadersInformationComponent', () => {
    let component: ExerciseHeadersInformationComponent;
    let fixture: ComponentFixture<ExerciseHeadersInformationComponent>;
    let exerciseService: ExerciseService;
    let getExerciseDetailsMock: jest.SpyInstance;

    const exercise = { id: 42, type: ExerciseType.TEXT, studentParticipations: [], course: {}, dueDate: dayjs().subtract(1, 'weeks') } as unknown as Exercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseHeadersInformationComponent, ArtemisTestModule, TranslateModule.forRoot(), NgbTooltipModule, InformationBoxComponent],
            providers: [MockProvider(ExerciseService), MockProvider(ArtemisDatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHeadersInformationComponent);
                component = fixture.componentInstance;
                // mock exerciseService
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                getExerciseDetailsMock = jest.spyOn(exerciseService, 'getExerciseDetails');
                getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: exercise } }));
            });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExerciseHeadersInformationComponent);
        component = fixture.componentInstance;
        component.exercise = { ...exercise };
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display the information box items', () => {
        component.informationBoxItems = [
            {
                title: 'Test Title 1',
                tooltip: 'Test Tooltip 1',
                tooltipParams: {},
                isContentComponent: false,
                content: { type: 'string', value: 'Test Content' },
                contentColor: 'primary',
            },
            {
                title: 'Test Title 2',
                tooltip: 'Test Tooltip 2',
                tooltipParams: {},
                isContentComponent: false,
                content: { type: 'string', value: 'Test Content' },
            },
        ];
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const informationBoxes = compiled.querySelectorAll('jhi-information-box');
        expect(informationBoxes).toHaveLength(2);
    });

    it('should display difficulty level component when content type is difficultyLevel', () => {
        component.informationBoxItems = [
            {
                title: 'Difficulty Level',
                tooltip: 'Difficulty Tooltip',
                tooltipParams: {},
                isContentComponent: true,
                content: { type: 'difficultyLevel', value: DifficultyLevel.EASY },
            },
        ];
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const difficultyLevelComponent = compiled.querySelector('jhi-difficulty-level');
        expect(difficultyLevelComponent).toBeTruthy();
    });
});
