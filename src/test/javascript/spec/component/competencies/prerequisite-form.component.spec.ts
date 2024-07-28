import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
import dayjs from 'dayjs';
import { Dayjs } from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';
import { PrerequisiteFormComponent } from 'app/course/competencies/prerequisite-form/prerequisite-form.component';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ArtemisTestModule } from '../../test.module';

describe('PrerequisiteFormComponent', () => {
    let componentFixture: ComponentFixture<PrerequisiteFormComponent>;
    let component: PrerequisiteFormComponent;
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
            imports: [PrerequisiteFormComponent, ArtemisTestModule],
            providers: [
                provideHttpClient(),
                MockProvider(CompetencyService),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(PrerequisiteFormComponent);
                component = componentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should update description', () => {
        componentFixture.detectChanges();
        component.updateDescriptionControl('new description');

        expect(component['form'].controls.description.getRawValue()).toBe('new description');
    });

    it('should emit on submit', () => {
        component.prerequisite = prerequisite;
        componentFixture.detectChanges();

        component['form'].controls.title.setValue('new title');
        const submitSpy = jest.spyOn(component.onSubmit, 'emit');

        component.submit();

        prerequisite.title = 'new title';
        expect(submitSpy).toHaveBeenCalledWith(prerequisite);
    });

    it('should emit on cancel', () => {
        const cancelSpy = jest.spyOn(component.onCancel, 'emit');

        component.cancel();

        expect(cancelSpy).toHaveBeenCalled();
    });

    it('should suggest taxonomy when title changes', () => {
        const suggestTaxonomySpy = jest.spyOn(component, 'suggestTaxonomies');
        const translateSpy = createTranslateSpy();
        componentFixture.detectChanges();

        const titleInput = componentFixture.nativeElement.querySelector('#title');
        titleInput.value = 'Building a tool: create a plan and implement something!';
        titleInput.dispatchEvent(new Event('input'));

        expect(suggestTaxonomySpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledTimes(12);
        expect(component['suggestedTaxonomies']).toEqual(['artemisApp.competency.taxonomies.REMEMBER', 'artemisApp.competency.taxonomies.UNDERSTAND']);
    });

    function createTranslateSpy() {
        const translateService = TestBed.inject(TranslateService);
        return jest.spyOn(translateService, 'instant').mockImplementation((key) => {
            switch (key) {
                case 'artemisApp.competency.keywords.REMEMBER':
                    return 'Something';
                case 'artemisApp.competency.keywords.UNDERSTAND':
                    return 'invent, build';
                default:
                    return key;
            }
        });
    }
});
