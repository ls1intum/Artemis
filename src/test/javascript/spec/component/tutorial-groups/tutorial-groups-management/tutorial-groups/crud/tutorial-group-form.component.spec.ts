import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import {
    TutorialGroupFormComponent,
    TutorialGroupFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { Language } from 'app/entities/course.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from '../../../helpers/tutorialGroupFormsUtils';
import { User } from 'app/core/user/user.model';

@Component({ selector: 'jhi-markdown-editor', template: '' })
class MarkdownEditorStubComponent {
    @Input() markdown: string;
    @Input() enableResize = false;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('TutorialGroupFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFormComponent>;
    let component: TutorialGroupFormComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    // group
    const validTitle = 'ExampleTitle';
    let validTeachingAssistant: User;
    const validCampus = 'ExampleCampus';
    const validCapacity = 10;
    const validIsOnline = true;
    const validLanguage = Language.GERMAN;
    const validAdditionalInformation = 'ExampleAdditionalInformation';

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string, subFormName?: string) => void;

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
                fixture = TestBed.createComponent(TutorialGroupFormComponent);
                validTeachingAssistant = new User();
                validTeachingAssistant.login = 'testLogin';
                component = fixture.componentInstance;
                component.courseId = course.id;
                fixture.detectChanges();
                testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('form behaviour', function () {
        beforeEach(() => {
            fixture.detectChanges();
            clickSubmit = generateClickSubmitButton(component, fixture, {
                title: validTitle,
                teachingAssistant: validTeachingAssistant,
                capacity: validCapacity,
                isOnline: validIsOnline,
                language: validLanguage,
                campus: validCampus,
                additionalInformation: validAdditionalInformation,
            });
        });

        it('should initialize', () => {
            fixture.detectChanges();
            expect(component).not.toBeNull();
        });

        it('should block submit when required property is missing', fakeAsync(() => {
            const requiredGroupControlNames = ['title', 'teachingAssistant', 'isOnline', 'language'];
            for (const controlName of requiredGroupControlNames) {
                testFormIsInvalidOnMissingRequiredProperty(controlName);
            }
        }));

        it('should correctly set form values in edit mode', () => {
            component.isEditMode = true;
            const formData: TutorialGroupFormData = {
                title: validTitle,
                teachingAssistant: validTeachingAssistant,
                additionalInformation: validAdditionalInformation,
                campus: validCampus,
                capacity: validCapacity,
                isOnline: validIsOnline,
                language: validLanguage,
            };
            fixture.detectChanges();

            component.formData = formData;
            component.ngOnChanges();

            const formControlNames = ['title', 'teachingAssistant', 'campus', 'capacity', 'isOnline', 'language'];
            for (const controlName of formControlNames) {
                expect(component.form.get(controlName)!.value).toEqual(formData[controlName]);
            }
            expect(component.additionalInformation).toEqual(formData.additionalInformation);
        });

        it('should submit valid form', fakeAsync(() => {
            setValidFormValues();
            fixture.detectChanges();
            expect(component.form.valid).toBeTrue();
            expect(component.isSubmitPossible).toBeTrue();

            clickSubmit(true);
        }));
    });

    // === helper functions ===

    const setValidFormValues = () => {
        component.titleControl!.setValue(validTitle);
        component.teachingAssistantControl!.setValue(validTeachingAssistant);
        component.capacityControl!.setValue(validCapacity);
        component.isOnlineControl!.setValue(validIsOnline);
        component.languageControl!.setValue(validLanguage);
        component.campusControl!.setValue(validCampus);
        component.additionalInformation = validAdditionalInformation;
    };
});
