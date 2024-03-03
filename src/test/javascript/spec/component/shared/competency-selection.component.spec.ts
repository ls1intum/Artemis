import { ArtemisTestModule } from '../../test.module';
import { CompetencySelectionComponent } from 'app/shared/competency-selection/competency-selection.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { NgModel, ReactiveFormsModule } from '@angular/forms';
import { Competency } from 'app/entities/competency.model';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ChangeDetectorRef } from '@angular/core';

describe('CompetencySelection', () => {
    let fixture: ComponentFixture<CompetencySelectionComponent>;
    let component: CompetencySelectionComponent;
    let courseStorageService: CourseStorageService;
    let competencyService: CompetencyService;

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
                competencyService = TestBed.inject(CompetencyService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should get competencies from cache', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [nonOptional, optional] });
        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse');

        fixture.detectChanges();

        const selector = fixture.debugElement.nativeElement.querySelector('#competency-selector');
        expect(component.value).toBeUndefined();
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).not.toHaveBeenCalled();
        expect(component.isLoading).toBeFalse();
        expect(component.competencies).toBeArrayOfSize(2);
        expect(selector).not.toBeNull();
    });

    it('should get competencies from service', () => {
        const nonOptional = { id: 1, optional: false } as Competency;
        const optional = { id: 2, optional: true } as Competency;
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [nonOptional, optional] })));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.competencies).toBeArrayOfSize(2);
        expect(component.competencies?.first()?.course).toBeUndefined();
        expect(component.competencies?.first()?.userProgress).toBeUndefined();
    });

    it('should set disabled when error during loading', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: undefined });
        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError({ status: 500 }));

        fixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.disabled).toBeTrue();
    });

    it('should be hidden when no competencies', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [] });
        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));

        fixture.detectChanges();

        const select = fixture.debugElement.query(By.css('select'));
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(component.isLoading).toBeFalse();
        expect(component.competencies).toBeEmpty();
        expect(select).toBeNull();
    });

    it('should select competencies when value is written', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ competencies: [{ id: 1, title: 'test' } as Competency] });

        fixture.detectChanges();

        component.writeValue([{ id: 1, title: 'other' } as Competency]);
        expect(component.value).toBeArrayOfSize(1);
        expect(component.value?.first()?.title).toBe('test');
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
        expect(component.value).toBeUndefined();

        component.toggleCompetency(competency1);
        component.toggleCompetency(competency2);
        component.toggleCompetency(competency3);

        expect(component.value).toHaveLength(3);
        expect(component.value).toContain(competency3);

        component.toggleCompetency(competency2);

        expect(component.value).toHaveLength(2);
        expect(component.value).not.toContain(competency2);

        component.toggleCompetency(competency1);
        component.toggleCompetency(competency3);

        expect(component.value).toBeUndefined();
    });

    it('should register onchange', () => {
        component.checkboxStates = {};
        const registerSpy = jest.fn();
        component.registerOnChange(registerSpy);
        component.toggleCompetency({ id: 1 });
        expect(registerSpy).toHaveBeenCalled();
    });

    it('should set disabled state', () => {
        component.disabled = true;
        component.setDisabledState?.(false);
        expect(component.disabled).toBeFalse();
    });
});
