import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import {
    TutorialGroupFormComponent,
    TutorialGroupFormData,
} from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { EventEmitter, Input, Output } from '@angular/core';
import { Component } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({ selector: 'jhi-markdown-editor', template: '' })
class MarkdownEditorStubComponent {
    @Input() markdown: string;
    @Input() enableResize = false;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('TutorialGroupFormComponent', () => {
    let tutorialGroupFormComponentFixture: ComponentFixture<TutorialGroupFormComponent>;
    let tutorialGroupFormComponent: TutorialGroupFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTypeaheadModule],
            declarations: [TutorialGroupFormComponent, MarkdownEditorStubComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
            providers: [
                MockProvider(CourseManagementService, {
                    getAllUsersInCourseGroup: () => {
                        return of(new HttpResponse({ body: [] }));
                    },
                }),
                MockProvider(TutorialGroupsService, {
                    getUniqueCampusValues: () => {
                        return of(new HttpResponse({ body: [] }));
                    },
                }),
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                tutorialGroupFormComponentFixture = TestBed.createComponent(TutorialGroupFormComponent);
                tutorialGroupFormComponent = tutorialGroupFormComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        tutorialGroupFormComponentFixture.detectChanges();
        expect(tutorialGroupFormComponent).not.toBeNull();
    });

    it('should not submit a form when title is missing', () => {
        tutorialGroupFormComponentFixture.detectChanges();
        tutorialGroupFormComponent.titleControl!.setValue(' ');

        tutorialGroupFormComponentFixture.detectChanges();
        expect(tutorialGroupFormComponent.form.invalid).toBeTrue();

        const submitFormSpy = jest.spyOn(tutorialGroupFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(tutorialGroupFormComponent.formSubmitted, 'emit');

        const submitButton = tutorialGroupFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return tutorialGroupFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).not.toHaveBeenCalled();
            expect(submitFormEventSpy).not.toHaveBeenCalled();
        });
    });

    it('should submit valid form', () => {
        tutorialGroupFormComponentFixture.detectChanges();
        const exampleTeachingAssistant = new User();
        exampleTeachingAssistant.login = 'testLogin';

        tutorialGroupFormComponent.titleControl!.setValue('example');
        tutorialGroupFormComponent.capacityControl!.setValue(1);
        tutorialGroupFormComponent.isOnlineControl?.setValue(true);
        tutorialGroupFormComponent.teachingAssistantControl!.setValue(exampleTeachingAssistant);
        tutorialGroupFormComponent.languageControl!.setValue('GERMAN');
        tutorialGroupFormComponent.campusControl!.setValue('Garching');

        tutorialGroupFormComponentFixture.detectChanges();
        expect(tutorialGroupFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(tutorialGroupFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(tutorialGroupFormComponent.formSubmitted, 'emit');

        const submitButton = tutorialGroupFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return tutorialGroupFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledWith({
                title: 'example',
                teachingAssistant: exampleTeachingAssistant,
                capacity: 1,
                isOnline: true,
                language: 'GERMAN',
                campus: 'Garching',
            });

            submitFormSpy.mockRestore();
            submitFormEventSpy.mockRestore();
        });
    });

    it('should correctly set form values in edit mode', () => {
        tutorialGroupFormComponent.isEditMode = true;
        const exampleTeachingAssistant = new User();
        exampleTeachingAssistant.login = 'testLogin';
        const formData: TutorialGroupFormData = {
            title: 'test',
            teachingAssistant: exampleTeachingAssistant,
        };
        tutorialGroupFormComponentFixture.detectChanges();

        tutorialGroupFormComponent.formData = formData;
        tutorialGroupFormComponent.ngOnChanges();

        expect(tutorialGroupFormComponent.titleControl?.value).toEqual(formData.title);
        expect(tutorialGroupFormComponent.teachingAssistantControl?.value.login).toEqual(exampleTeachingAssistant.login);
        expect(tutorialGroupFormComponent.teachingAssistantControl?.value.label).toBe('(testLogin)');
    });
});
