import { ArtemisTestModule } from '../../test.module';
import { CompetencySelectionComponent } from 'app/shared/competency-selection/competency-selection.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { NgModel, ReactiveFormsModule } from '@angular/forms';
import { Competency, CompetencyLearningObjectLink } from 'app/entities/competency.model';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ChangeDetectorRef } from '@angular/core';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

describe('CompetencySelection', () => {
    let fixture: ComponentFixture<CompetencySelectionComponent>;
    let component: CompetencySelectionComponent;
    let courseStorageService: CourseStorageService;
    let courseCompetencyService: CourseCompetencyService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, MockModule(NgbTooltipModule)],
            declarations: [CompetencySelectionComponent, MockComponent(FaIconComponent), MockDirective(NgModel)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 1 }),
                        },
                    } as any as ActivatedRoute,
                },
                {
                    provide: Router,
                    useClass: MockRouter,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencySelectionComponent);
                component = fixture.componentInstance;
                courseStorageService = TestBed.inject(CourseStorageService);
                courseCompetencyService = TestBed.inject(CourseCompetencyService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should get competencies from cache', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [nonOptional, optional] });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse');

        fixture.detectChanges();

        const selector = fixture.debugElement.nativeElement.querySelector('#competency-selector');
        expect(component.selectedCompetencyLinks).toBeUndefined();
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).not.toHaveBeenCalled();
        expect(component.isLoading).toBeFalse();
        expect(component.competencyLinks).toBeArrayOfSize(2);
        expect(selector).not.toBeNull();
    });

    it('should get competencies from service', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [nonOptional, optional] })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.competencyLinks).toBeArrayOfSize(2);
        expect(component.competencyLinks?.first()?.competency?.course).toBeUndefined();
        expect(component.competencyLinks?.first()?.competency?.userProgress).toBeUndefined();
    });

    it('should set disabled when error during loading', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(throwError({ status: 500 }));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.disabled).toBeTrue();
    });

    it('should be hidden when no competencies', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [] });
        const getAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.competencyLinks).toBeEmpty();
        expect(select).toBeNull();
    });

    it('should select competencies when value is written', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [{ id: 1, title: 'test' } as Competency] });

        fixture.detectChanges();

        component.writeValue([new CompetencyLearningObjectLink({ id: 1, title: 'other' } as Competency, 1)]);
        expect(component.selectedCompetencyLinks).toBeArrayOfSize(1);
        expect(component.selectedCompetencyLinks?.first()?.competency?.title).toBe('test');
    });

    it('should trigger change detection after loading competencies', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
        const detectChangesStub = jest.spyOn(changeDetector.constructor.prototype, 'detectChanges');

        fixture.detectChanges();

        expect(detectChangesStub).toHaveBeenCalledOnce();
    });

    it('should select / unselect competencies', () => {
        const competency1 = { id: 1, optional: false } as Competency;
        const competency2 = { id: 2, optional: true } as Competency;
        const competency3 = { id: 3, optional: false } as Competency;
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [competency1, competency2, competency3] });

        fixture.detectChanges();
        expect(component.selectedCompetencyLinks).toBeUndefined();

        component.toggleCompetency(new CompetencyLearningObjectLink(competency1, 1));
        component.toggleCompetency(new CompetencyLearningObjectLink(competency2, 1));
        component.toggleCompetency(new CompetencyLearningObjectLink(competency3, 1));

        expect(component.selectedCompetencyLinks).toHaveLength(3);
        expect(component.selectedCompetencyLinks).toContainEqual(new CompetencyLearningObjectLink(competency3, 1));

        component.toggleCompetency(new CompetencyLearningObjectLink(competency2, 1));

        expect(component.selectedCompetencyLinks).toHaveLength(2);
        expect(component.selectedCompetencyLinks).not.toContainEqual(new CompetencyLearningObjectLink(competency2, 1));

        component.toggleCompetency(new CompetencyLearningObjectLink(competency1, 1));
        component.toggleCompetency(new CompetencyLearningObjectLink(competency3, 1));

        expect(component.selectedCompetencyLinks).toBeUndefined();
    });

    it('should register onchange', () => {
        component.checkboxStates = {};
        const registerSpy = jest.fn();
        component.registerOnChange(registerSpy);
        component.toggleCompetency(new CompetencyLearningObjectLink({ id: 1 }, 1));
        expect(registerSpy).toHaveBeenCalled();
    });

    it('should set disabled state', () => {
        component.disabled = true;
        component.setDisabledState?.(false);
        expect(component.disabled).toBeFalse();
    });
});
