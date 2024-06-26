import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { EditPrerequisiteComponent } from 'app/course/competencies/prerequisite-form/edit-prerequisite.component';
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

describe('EditPrerequisiteComponent', () => {
    let componentFixture: ComponentFixture<EditPrerequisiteComponent>;
    let component: EditPrerequisiteComponent;
    const prerequisite: Prerequisite = {
        id: 1,
        title: 'Title1',
        description: 'Description1',
        taxonomy: CompetencyTaxonomy.APPLY,
        masteryThreshold: 50,
        optional: true,
        softDueDate: dayjs('2022-02-20') as Dayjs,
    };
    let prerequisiteService: PrerequisiteService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [EditPrerequisiteComponent],
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
            .overrideComponent(EditPrerequisiteComponent, {
                remove: {
                    imports: [PrerequisiteFormComponent],
                },
                add: {
                    imports: [PrerequisiteFormStubComponent],
                },
            })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(EditPrerequisiteComponent);
                component = componentFixture.componentInstance;
                prerequisiteService = TestBed.inject(PrerequisiteService);
                jest.spyOn(prerequisiteService, 'getPrerequisite').mockReturnValue(of(prerequisite));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize correctly', () => {
        componentFixture.detectChanges();

        expect(component.existingPrerequisite).toEqual(prerequisite);
        expect(component.isLoading).toBeFalse();
    });

    it('should navigate back after updating prerequisite', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const updatedPrerequisite: Prerequisite = { ...prerequisite, title: 'new title', description: 'new description' };
        const updateSpy = jest.spyOn(prerequisiteService, 'updatePrerequisite').mockReturnValue(of(updatedPrerequisite));

        componentFixture.detectChanges();
        const prerequisiteForm: PrerequisiteFormStubComponent = componentFixture.debugElement.query(By.directive(PrerequisiteFormStubComponent)).componentInstance;
        prerequisiteForm.onSubmit.emit(updatedPrerequisite);

        expect(updateSpy).toHaveBeenCalledWith(updatedPrerequisite, updatedPrerequisite.id, component.courseId);
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
        jest.spyOn(prerequisiteService, 'updatePrerequisite').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

        componentFixture.detectChanges();
        component.updatePrerequisite(prerequisite);

        expect(errorSpy).toHaveBeenCalled();
    });
});
