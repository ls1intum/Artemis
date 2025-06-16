import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { CreateCompetencyComponent } from 'app/atlas/manage/create/create-competency.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { HttpResponse } from '@angular/common/http';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { By } from '@angular/platform-browser';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';

import { CompetencyFormComponent } from 'app/atlas/manage/forms/competency/competency-form.component';

import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CreateCompetency', () => {
    let createCompetencyComponentFixture: ComponentFixture<CreateCompetencyComponent>;
    let createCompetencyComponent: CreateCompetencyComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CreateCompetencyComponent, CompetencyFormComponent],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(DocumentationButtonComponent), MockComponent(CompetencyFormComponent), MockDirective(TranslateDirective)],
            providers: [
                MockProvider(CompetencyService),
                MockProvider(LectureService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'courseId':
                                                return 1;
                                        }
                                    },
                                }),
                            },
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                createCompetencyComponentFixture = TestBed.createComponent(CreateCompetencyComponent);
                createCompetencyComponent = createCompetencyComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        createCompetencyComponentFixture.detectChanges();
        expect(createCompetencyComponent).toBeDefined();
    });

    it('should set lecture units', () => {
        const lectureService = TestBed.inject(LectureService);
        const lecture: Lecture = {
            id: 1,
            lectureUnits: [{ id: 1, type: LectureUnitType.TEXT }],
        };
        const lecturesResponse = new HttpResponse({
            body: [lecture],
            status: 200,
        });
        jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(of(lecturesResponse));

        createCompetencyComponentFixture.detectChanges();

        expect(createCompetencyComponent.lecturesWithLectureUnits).toEqual([lecture]);
    });

    it('should set empty array of lecture units if lecture has none', () => {
        const lectureService = TestBed.inject(LectureService);
        const lecture: Lecture = { id: 1, lectureUnits: undefined };
        const expectedLecture: Lecture = { id: 1, lectureUnits: [] };
        const lecturesResponse = new HttpResponse({
            body: [lecture],
            status: 200,
        });
        jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(of(lecturesResponse));

        createCompetencyComponentFixture.detectChanges();

        expect(createCompetencyComponent.lecturesWithLectureUnits).toEqual([expectedLecture]);
    });

    it('should send POST request upon form submission and navigate', async () => {
        const router: Router = TestBed.inject(Router);
        const competencyService = TestBed.inject(CompetencyService);

        const formData: CourseCompetencyFormData = {
            title: 'Test',
            description: 'Lorem Ipsum',
            optional: true,
            masteryThreshold: 100,
        };

        const response: HttpResponse<Competency> = new HttpResponse({
            body: {},
            status: 201,
        });

        const createSpy = jest.spyOn(competencyService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createCompetencyComponentFixture.detectChanges();

        const competencyForm = createCompetencyComponentFixture.debugElement.query(By.directive(CompetencyFormComponent)).componentInstance;
        competencyForm.formSubmitted.emit(formData);

        return createCompetencyComponentFixture.whenStable().then(() => {
            const competency: Competency = new Competency();
            competency.title = formData.title;
            competency.description = formData.description;
            competency.optional = formData.optional;

            expect(createSpy).toHaveBeenCalledWith(competency, 1);
            expect(createSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    });

    it('should not create competency if title is missing', () => {
        const competencyService = TestBed.inject(CompetencyService);

        const formData: CourseCompetencyFormData = {
            title: undefined,
            description: 'Lorem Ipsum',
            optional: true,
        };

        const createSpy = jest.spyOn(competencyService, 'create');

        createCompetencyComponent.createCompetency(formData);

        expect(createSpy).not.toHaveBeenCalled();
    });
});
