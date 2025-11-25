import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CreatePrerequisiteComponent } from 'app/atlas/manage/create/create-prerequisite.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { PrerequisiteFormComponent } from 'app/atlas/manage/forms/prerequisite/prerequisite-form.component';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';

describe('CreatePrerequisite', () => {
    let createPrerequisiteComponentFixture: ComponentFixture<CreatePrerequisiteComponent>;
    let createPrerequisiteComponent: CreatePrerequisiteComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CreatePrerequisiteComponent, PrerequisiteFormComponent],
            declarations: [
                MockPipe(ArtemisTranslatePipe),
                MockComponent(DocumentationButtonComponent),
                MockComponent(PrerequisiteFormComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(PrerequisiteService),
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
                createPrerequisiteComponentFixture = TestBed.createComponent(CreatePrerequisiteComponent);
                createPrerequisiteComponent = createPrerequisiteComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        createPrerequisiteComponentFixture.detectChanges();
        expect(createPrerequisiteComponent).toBeDefined();
    });

    it('should send POST request upon form submission and navigate', async () => {
        const router: Router = TestBed.inject(Router);
        const prerequisiteService = TestBed.inject(PrerequisiteService);

        const formData: CourseCompetencyFormData = {
            title: 'Test',
            description: 'Lorem Ipsum',
            optional: true,
            masteryThreshold: 100,
        };

        const response: HttpResponse<Prerequisite> = new HttpResponse({
            body: {},
            status: 201,
        });

        const createSpy = jest.spyOn(prerequisiteService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createPrerequisiteComponentFixture.detectChanges();

        const competencyForm = createPrerequisiteComponentFixture.debugElement.query(By.directive(PrerequisiteFormComponent)).componentInstance;
        competencyForm.formSubmitted.emit(formData);

        return createPrerequisiteComponentFixture.whenStable().then(() => {
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
        const prerequisiteService = TestBed.inject(PrerequisiteService);

        const formData: CourseCompetencyFormData = {
            title: undefined,
            description: 'Lorem Ipsum',
            optional: true,
        };

        const createSpy = jest.spyOn(prerequisiteService, 'create');

        createPrerequisiteComponent.createPrerequisite(formData);

        expect(createSpy).not.toHaveBeenCalled();
    });
});
