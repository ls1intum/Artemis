import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
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
                                paramMap: of(convertToParamMap({ courseId: 1 })),
                                snapshot: { paramMap: convertToParamMap({ courseId: 1 }) },
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
            expect(createSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    title: formData.title,
                    description: formData.description,
                    softDueDate: formData.softDueDate,
                    taxonomy: formData.taxonomy,
                    masteryThreshold: formData.masteryThreshold,
                    optional: formData.optional,
                }),
                1,
            );
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
