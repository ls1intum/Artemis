import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs/internal/observable/of';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { JhiSortByDirective, JhiSortDirective } from 'ng-jhipster';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MomentModule } from 'ngx-moment';
import { CourseExercisesComponent, ExerciseFilter, ExerciseSortingOrder, SortingAttribute } from 'app/overview/course-exercises/course-exercises.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ActivatedRoute } from '@angular/router';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { stub } from 'sinon';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import * as sinon from 'sinon';
import * as moment from 'moment';
import { Subject } from 'rxjs';
import { MockTranslateValuesDirective } from './course-scores/course-scores.component.spec';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseExercisesComponent', () => {
    let fixture: ComponentFixture<CourseExercisesComponent>;
    let component: CourseExercisesComponent;
    let service: CourseManagementService;
    let courseCalculation: CourseScoreCalculationService;
    let translateService: TranslateService;
    let exerciseService: ExerciseService;
    let localStorageService: LocalStorageService;

    let course: Course;
    let exercise: Exercise;
    let courseCalculationSpy: sinon.SinonStub;

    const parentRoute = ({ params: of({ courseId: '123' }) } as any) as ActivatedRoute;
    const route = ({ parent: parentRoute } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule],
            declarations: [
                CourseExercisesComponent,
                MockDirective(OrionFilterDirective),
                MockComponent(AlertComponent),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(SidePanelComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(JhiSortByDirective),
                MockPipe(TranslatePipe),
                MockDirective(JhiSortDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockTranslateValuesDirective,
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
                courseCalculation = TestBed.inject(CourseScoreCalculationService);
                translateService = TestBed.inject(TranslateService);
                exerciseService = TestBed.inject(ExerciseService);
                localStorageService = TestBed.inject(LocalStorageService);

                course = new Course();
                course.id = 123;
                exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
                exercise.dueDate = moment('2021-01-13T16:11:00+01:00').add(1, 'days');
                exercise.releaseDate = moment('2021-01-13T16:11:00+01:00').subtract(1, 'days');
                course.exercises = [exercise];
                spyOn(service, 'getCourseUpdates').and.returnValue(of(course));
                spyOn(translateService, 'onLangChange').and.returnValue(of(new Subject()));
                spyOn(localStorageService, 'retrieve').and.returnValue('OVERDUE,NEEDS_WORK');
                courseCalculationSpy = stub(courseCalculation, 'getCourse').returns(course);

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        component.ngOnDestroy();
        sinon.restore();
    });

    it('should initialize', () => {
        expect(component.course).to.deep.equal(course);
        expect(courseCalculationSpy.callCount).to.equal(2);
        expect(courseCalculationSpy.getCall(0).calledWithExactly(course.id)).to.be.true;
        expect(courseCalculationSpy.getCall(1).calledWithExactly(course.id)).to.be.true;
    });

    it('should invoke setSortingAttribute', () => {
        const sortingButton = fixture.debugElement.query(By.css('#dueDateSorting'));
        expect(sortingButton).to.exist;
        sortingButton.nativeElement.click();
        expect(component.sortingAttribute).to.equal(SortingAttribute.DUE_DATE);
    });

    it('should react to changes', () => {
        spyOn(exerciseService, 'getNextExerciseForHours').and.returnValue(exercise);
        component.ngOnChanges();
        expect(component.nextRelevantExercise).to.deep.equal(exercise);
    });

    it('should reorder all exercises', () => {
        component.sortingOrder = ExerciseSortingOrder.DESC;
        const sortingButton = fixture.debugElement.query(By.css('#flip'));
        expect(sortingButton).to.exist;
        sortingButton.nativeElement.click();
        expect(component.sortingOrder).to.equal(ExerciseSortingOrder.ASC);
    });

    it('should filter all exercises in different situations', () => {
        component.sortingOrder = ExerciseSortingOrder.DESC;
        const filters: ExerciseFilter[] = Object.values(ExerciseFilter);
        const localStorageSpy = sinon.spy(localStorageService, 'store');

        component.toggleFilters(filters);

        expect(localStorageSpy).to.have.been.calledOnce;
        expect(component.activeFilters).to.deep.equal(new Set());

        for (let i = 0; i < 7; i++) {
            const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
            newExercise.dueDate = moment('2021-01-13T16:11:00+01:00').add(1 + i, 'days');
            newExercise.releaseDate = moment('2021-01-13T16:11:00+01:00').subtract(1 + i, 'days');
            component.course?.exercises?.push(newExercise);
        }
        const anotherNewExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        component.course?.exercises?.push(anotherNewExercise);

        component.activeFilters.clear();
        component.activeFilters.add(ExerciseFilter.OVERDUE);

        component.toggleFilters(filters);

        expect(component.activeFilters).to.deep.equal(new Set().add(ExerciseFilter.NEEDS_WORK));
        expect(Object.keys(component.weeklyExercisesGrouped)).to.deep.equal(['2021-01-17', '2021-01-10', 'noDate']);
        expect(component.weeklyIndexKeys).to.deep.equal(['2021-01-17', '2021-01-10', 'noDate']);
        expect(component.exerciseCountMap.get('modeling')).to.equal(9);

        // trigger updateUpcomingExercises dynamically with moment()
        component.course!.exercises = [];
        for (let i = 0; i < 7; i++) {
            const newExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
            newExercise.dueDate = moment().add(1 + i, 'days');
            newExercise.releaseDate = moment().subtract(1 + i, 'days');
            component.course?.exercises?.push(newExercise);
        }

        component.toggleFilters(filters);

        expect(component.upcomingExercises.length).to.equal(5);
    });
});
