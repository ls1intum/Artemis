import { ComponentFixture, TestBed, fakeAsync, waitForAsync } from '@angular/core/testing';
import {
    AddUsersFormData,
    ConversationAddUsersFormComponent,
} from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MockComponent, MockPipe } from 'ng-mocks';
import { CourseUsersSelectorComponent } from 'app/shared/course-users-selector/course-users-selector.component';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from '../../../helpers/conversationExampleModels';
import { Course } from 'app/entities/course.model';
import { isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { By } from '@angular/platform-browser';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

const examples: ConversationDTO[] = [generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];
examples.forEach((activeConversation) => {
    describe('ConversationAddUsersFormComponent with ' + activeConversation.type, () => {
        let component: ConversationAddUsersFormComponent;
        let fixture: ComponentFixture<ConversationAddUsersFormComponent>;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ReactiveFormsModule, FormsModule],
                declarations: [ConversationAddUsersFormComponent, MockComponent(CourseUsersSelectorComponent), MockPipe(ArtemisTranslatePipe)],
            }).compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ConversationAddUsersFormComponent);
            component = fixture.componentInstance;
            component.courseId = course.id!;
            component.activeConversation = activeConversation;
            component.maxSelectable = 2;
            fixture.detectChanges();
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.mode).toBe('individual');
        });

        it('should allow to switch to group mode only for channels', () => {
            if (isChannelDTO(activeConversation)) {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBeFalse();
            } else {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBeTrue();
            }
        });

        it('should hide parts of the form depending on which mode is selected', () => {
            if (isChannelDTO(activeConversation)) {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBeFalse();
                expect(component.mode).toBe('individual');
                expect(fixture.debugElement.query(By.css('.individual-select')).nativeElement.hidden).toBeFalse();
                expect(fixture.debugElement.query(By.css('.group-select')).nativeElement.hidden).toBeTrue();
                fixture.debugElement.query(By.css('#group')).nativeElement.click();
                fixture.detectChanges();
                expect(component.mode).toBe('group');
                expect(fixture.debugElement.query(By.css('.individual-select')).nativeElement.hidden).toBeTrue();
                expect(fixture.debugElement.query(By.css('.group-select')).nativeElement.hidden).toBeFalse();
            } else {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBeTrue();
                expect(component.mode).toBe('individual');
                expect(fixture.debugElement.query(By.css('.individual-select')).nativeElement.hidden).toBeFalse();
                expect(fixture.debugElement.query(By.css('.group-select')).nativeElement.hidden).toBeTrue();
            }
        });

        it('should block submit when no user is selected', fakeAsync(() => {
            setFormValid();
            setSelectedUsers(undefined);
            checkFormIsInvalid();

            setFormValid();
            setSelectedUsers([]);
            checkFormIsInvalid();
        }));

        it('should block when too many users are selected', fakeAsync(() => {
            setFormValid();
            setSelectedUsers([{ id: 1 } as UserPublicInfoDTO, { id: 2 } as UserPublicInfoDTO, { id: 3 } as UserPublicInfoDTO]);
            checkFormIsInvalid();
        }));

        it('should block when in group mode and no mode is selected', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                setFormValid();
                fixture.debugElement.query(By.css('#group')).nativeElement.click();
                fixture.detectChanges();
                expect(component.mode).toBe('group');
                expect(component.isSubmitPossible).toBeFalse();
            }
        }));

        it('should submit valid form in individual mode', fakeAsync(() => {
            setValidIndividualModeFormValues();
            fixture.detectChanges();
            expect(component.form.valid).toBeTrue();
            expect(component.isSubmitPossible).toBeTrue();
            expect(component.mode).toBe('individual');

            const expectedAddUsersFormData: AddUsersFormData = {
                selectedUsers: [{ id: 1, name: 'test' } as UserPublicInfoDTO],
                addAllStudents: false,
                addAllTutors: false,
                addAllInstructors: false,
            };

            clickSubmitButton(true, expectedAddUsersFormData);
        }));

        it('should submit valid form in group mode', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                fixture.debugElement.query(By.css('#group')).nativeElement.click();
                fixture.detectChanges();
                setValidGroupModeFormValues();
                fixture.detectChanges();
                expect(component.isSubmitPossible).toBeTrue();
                expect(component.mode).toBe('group');

                const expectedAddUsersFormData: AddUsersFormData = {
                    addAllStudents: true,
                    addAllTutors: false,
                    addAllInstructors: false,
                    selectedUsers: [],
                };

                clickSubmitButton(true, expectedAddUsersFormData);
            }
        }));

        function setSelectedUsers(selectedUsers?: UserPublicInfoDTO[]) {
            component!.selectedUsersControl!.setValue(selectedUsers);
        }
        const setValidIndividualModeFormValues = () => {
            if (component) {
                component!.selectedUsersControl!.setValue([{ id: 1, name: 'test' } as UserPublicInfoDTO]);
            }
        };

        const setValidGroupModeFormValues = () => {
            if (component) {
                component.form.get('addAllStudents')!.setValue(true);
            }
        };

        function checkFormIsInvalid() {
            fixture.detectChanges();
            expect(component.form.invalid).toBeTrue();
            expect(component.isSubmitPossible).toBeFalse();
            clickSubmitButton(false);
        }
        function setFormValid() {
            setValidIndividualModeFormValues();
            fixture.detectChanges();
            expect(component.form.valid).toBeTrue();
            expect(component.isSubmitPossible).toBeTrue();
        }
        const clickSubmitButton = (expectSubmitEvent: boolean, expectedFormData?: AddUsersFormData) => {
            const submitFormSpy = jest.spyOn(component, 'submitForm');
            const submitFormEventSpy = jest.spyOn(component.formSubmitted, 'emit');

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
            submitButton.click();

            return fixture.whenStable().then(() => {
                if (expectSubmitEvent) {
                    expect(submitFormSpy).toHaveBeenCalledOnce();
                    expect(submitFormEventSpy).toHaveBeenCalledOnce();
                    expect(submitFormEventSpy).toHaveBeenCalledWith(expectedFormData);
                } else {
                    expect(submitFormSpy).not.toHaveBeenCalled();
                    expect(submitFormEventSpy).not.toHaveBeenCalled();
                }
            });
        };
    });
});
