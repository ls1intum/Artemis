import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseCompetenciesDetailsComponent } from 'app/overview/course-competencies/course-competencies-details.component';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FireworksComponent } from 'app/shared/fireworks/fireworks.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Competency, CompetencyProgress } from 'app/entities/competency.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { By } from '@angular/platform-browser';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import dayjs from 'dayjs/esm';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('CourseCompetenciesDetails', () => {
    let fixture: ComponentFixture<CourseCompetenciesDetailsComponent>;
    let component: CourseCompetenciesDetailsComponent;

    let competencyService: CompetencyService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule, MockModule(NgbTooltipModule)],
            declarations: [
                CourseCompetenciesDetailsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(MockHasAnyAuthorityDirective),
                MockComponent(AttachmentUnitComponent),
                MockComponent(ExerciseUnitComponent),
                MockComponent(TextUnitComponent),
                MockComponent(VideoUnitComponent),
                MockComponent(OnlineUnitComponent),
                MockComponent(CompetencyRingsComponent),
                MockComponent(SidePanelComponent),
                MockComponent(HelpIconComponent),
                MockComponent(FaIconComponent),
                MockComponent(FireworksComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(HtmlForMarkdownPipe),
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(AlertService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ competencyId: '1', courseId: '1' }),
                    },
                },
                { provide: Router, useValue: MockRouter },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseCompetenciesDetailsComponent);
                component = fixture.componentInstance;
                competencyService = TestBed.inject(CompetencyService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should load competency to display progress and all lecture units', () => {
        const competency = {
            id: 1,
            lectureUnits: [new TextUnit()],
            exercises: [{ id: 5 } as TextExercise],
        } as Competency;
        const findByIdSpy = jest.spyOn(competencyService, 'findById').mockReturnValue(of(new HttpResponse({ body: competency })));

        fixture.detectChanges();

        const textUnit = fixture.debugElement.query(By.directive(TextUnitComponent));
        const exerciseUnit = fixture.debugElement.query(By.directive(ExerciseUnitComponent));

        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(component.competency.lectureUnits).toHaveLength(2);
        expect(textUnit).not.toBeNull();
        expect(exerciseUnit).not.toBeNull();
    });

    it('should load competency to display progress and the exercise unit', () => {
        const competency = {
            id: 1,
            exercises: [{ id: 5 } as ModelingExercise],
        } as Competency;
        const findByIdSpy = jest.spyOn(competencyService, 'findById').mockReturnValue(of(new HttpResponse({ body: competency })));

        fixture.detectChanges();

        const exerciseUnit = fixture.debugElement.query(By.directive(ExerciseUnitComponent));

        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(component.competency.lectureUnits).toHaveLength(1);
        expect(exerciseUnit).not.toBeNull();
    });

    it('should show fireworks when competency was mastered', fakeAsync(() => {
        const competency = {
            id: 1,
            userProgress: [
                {
                    progress: 100,
                    confidence: 100,
                } as CompetencyProgress,
            ],
        } as Competency;
        const findByIdSpy = jest.spyOn(competencyService, 'findById').mockReturnValue(of(new HttpResponse({ body: competency })));

        fixture.detectChanges();
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(component.isMastered).toBeTrue();

        component.showFireworksIfMastered();

        tick(1000);
        expect(component.showFireworks).toBeTrue();

        tick(5000);
        expect(component.showFireworks).toBeFalse();
    }));

    it('should detect if due date is passed', () => {
        const competencyFuture = { softDueDate: dayjs().add(1, 'days') } as Competency;
        component.competency = competencyFuture;
        fixture.detectChanges();
        expect(component.softDueDatePassed).toBeFalse();

        const competencyPast = { softDueDate: dayjs().subtract(1, 'days') } as Competency;
        component.competency = competencyPast;
        fixture.detectChanges();
        expect(component.softDueDatePassed).toBeTrue();
    });

    it.each([
        { competency: { softDueDate: dayjs().add(1, 'days') } as Competency, expectedBadge: 'success' },
        { competency: { softDueDate: dayjs().subtract(1, 'days') } as Competency, expectedBadge: 'danger' },
    ])('should have [ngClass] resolve to correct date badge', ({ competency, expectedBadge }) => {
        component.competency = competency;
        fixture.detectChanges();
        const badge = fixture.debugElement.query(By.css('#date-badge')).nativeElement;
        expect(badge.classList).toContain('bg-' + expectedBadge);
    });
});
