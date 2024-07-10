import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { CreatePrerequisiteComponent } from 'app/course/competencies/prerequisite-form/create-prerequisite.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
import dayjs from 'dayjs';
import { Dayjs } from 'dayjs/esm';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { PrerequisiteFormComponent } from 'app/course/competencies/prerequisite-form/prerequisite-form.component';
import { PrerequisiteFormStubComponent } from './prerequisite-form-stub.component';
import { By } from '@angular/platform-browser';

describe('CreatePrerequisiteComponent', () => {
    let componentFixture: ComponentFixture<CreatePrerequisiteComponent>;
    let component: CreatePrerequisiteComponent;
    let prerequisiteService: PrerequisiteService;
    const prerequisite: Prerequisite = {
        title: 'Title1',
        description: 'Description1',
        taxonomy: CompetencyTaxonomy.APPLY,
        masteryThreshold: 50,
        optional: true,
        softDueDate: dayjs('2022-02-20') as Dayjs,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CreatePrerequisiteComponent],
            providers: [
                provideHttpClient(),
                MockProvider(PrerequisiteService),
                MockProvider(AlertService),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({
                            prerequisiteId: 1,
                            courseId: 1,
                        }),
                    },
                },
            ],
        })
            .overrideComponent(CreatePrerequisiteComponent, {
                remove: {
                    imports: [PrerequisiteFormComponent],
                },
                add: {
                    imports: [PrerequisiteFormStubComponent],
                },
            })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CreatePrerequisiteComponent);
                component = componentFixture.componentInstance;
                prerequisiteService = TestBed.inject(PrerequisiteService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should navigate back after creating prerequisite', () => {
        const createSpy = jest.spyOn(prerequisiteService, 'createPrerequisite').mockReturnValue(of(prerequisite));
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        componentFixture.detectChanges();
        const prerequisiteForm: PrerequisiteFormStubComponent = componentFixture.debugElement.query(By.directive(PrerequisiteFormStubComponent)).componentInstance;
        prerequisiteForm.onSubmit.emit(prerequisite);

        expect(createSpy).toHaveBeenCalledWith(prerequisite, component.courseId);
        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should navigate on cancel', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        componentFixture.detectChanges();
        const prerequisiteForm: PrerequisiteFormStubComponent = componentFixture.debugElement.query(By.directive(PrerequisiteFormStubComponent)).componentInstance;
        prerequisiteForm.onCancel.emit();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should alert on error', () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = jest.spyOn(alertService, 'error');
        jest.spyOn(prerequisiteService, 'createPrerequisite').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

        componentFixture.detectChanges();
        component.createPrerequisite(prerequisite);

        expect(errorSpy).toHaveBeenCalled();
    });
});
