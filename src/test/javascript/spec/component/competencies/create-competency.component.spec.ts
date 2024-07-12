import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { CreateCompetencyComponent } from 'app/course/competencies/create-competency/create-competency.component';
import { CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { LectureService } from 'app/lecture/lecture.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { Competency } from 'app/entities/competency.model';
import { By } from '@angular/platform-browser';
import { CompetencyFormStubComponent } from './competency-form-stub.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

describe('CreateCompetency', () => {
    let createCompetencyComponentFixture: ComponentFixture<CreateCompetencyComponent>;
    let createCompetencyComponent: CreateCompetencyComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CompetencyFormStubComponent, CreateCompetencyComponent, MockPipe(ArtemisTranslatePipe), MockComponent(DocumentationButtonComponent)],
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
            schemas: [],
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

    it('should send POST request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const competencyService = TestBed.inject(CompetencyService);

        const textUnit: TextUnit = new TextUnit();
        textUnit.id = 1;
        const formData: CompetencyFormData = {
            title: 'Test',
            description: 'Lorem Ipsum',
            optional: true,
            connectedLectureUnits: [textUnit],
        };

        const response: HttpResponse<Competency> = new HttpResponse({
            body: {},
            status: 201,
        });

        const createSpy = jest.spyOn(competencyService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createCompetencyComponentFixture.detectChanges();

        const competencyForm: CompetencyFormStubComponent = createCompetencyComponentFixture.debugElement.query(By.directive(CompetencyFormStubComponent)).componentInstance;
        competencyForm.formSubmitted.emit(formData);

        return createCompetencyComponentFixture.whenStable().then(() => {
            const competency: Competency = new Competency();
            competency.title = formData.title;
            competency.description = formData.description;
            competency.optional = formData.optional;
            competency.lectureUnits = formData.connectedLectureUnits;

            expect(createSpy).toHaveBeenCalledWith(competency, 1);
            expect(createSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    });

    it('should not create competency if title is missing', () => {
        const competencyService = TestBed.inject(CompetencyService);

        const formData: CompetencyFormData = {
            title: undefined,
            description: 'Lorem Ipsum',
            optional: true,
        };

        const createSpy = jest.spyOn(competencyService, 'create');

        createCompetencyComponent.createCompetency(formData);

        expect(createSpy).not.toHaveBeenCalled();
    });
});
