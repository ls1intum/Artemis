import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { GroupChatDTO } from '../../../../shared/entities/conversation/group-chat.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { By } from '@angular/platform-browser';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import {
    AddUsersFormData,
    ConversationAddUsersFormComponent,
} from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';

const examples: ConversationDTO[] = [generateExampleGroupChatDTO({} as GroupChatDTO), generateExampleChannelDTO({} as ChannelDTO)];
examples.forEach((activeConversation) => {
    describe('ConversationAddUsersFormComponent with ' + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationAddUsersFormComponent;
        let fixture: ComponentFixture<ConversationAddUsersFormComponent>;
        const course = { id: 1 } as Course;

        beforeEach(async () => {
            TestBed.configureTestingModule({
                imports: [
                    ReactiveFormsModule,
                    FormsModule,
                    ConversationAddUsersFormComponent,
                    MockComponent(CourseUsersSelectorComponent),
                    MockPipe(ArtemisTranslatePipe),
                    MockDirective(TranslateDirective),
                ],
                providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            });
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(ConversationAddUsersFormComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('courseId', course.id!);
            fixture.componentRef.setInput('activeConversation', activeConversation);
            fixture.componentRef.setInput('maxSelectable', 2);
            fixture.detectChanges();
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.mode).toBe('individual');
        });

        it('should allow to switch to group mode only for channels', () => {
            if (isChannelDTO(activeConversation)) {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBe(false);
            } else {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBe(true);
            }
        });

        it('should hide parts of the form depending on which mode is selected', () => {
            if (isChannelDTO(activeConversation)) {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBe(false);
                expect(component.mode).toBe('individual');
                expect(fixture.debugElement.query(By.css('.individual-select')).nativeElement.hidden).toBe(false);
                expect(fixture.debugElement.query(By.css('.group-select')).nativeElement.hidden).toBe(true);
                fixture.debugElement.query(By.css('#group')).nativeElement.click();
                fixture.changeDetectorRef.detectChanges();
                expect(component.mode).toBe('group');
                expect(fixture.debugElement.query(By.css('.individual-select')).nativeElement.hidden).toBe(true);
                expect(fixture.debugElement.query(By.css('.group-select')).nativeElement.hidden).toBe(false);
            } else {
                expect(fixture.debugElement.query(By.css('.mode-switch')).nativeElement.hidden).toBe(true);
                expect(component.mode).toBe('individual');
                expect(fixture.debugElement.query(By.css('.individual-select')).nativeElement.hidden).toBe(false);
                expect(fixture.debugElement.query(By.css('.group-select')).nativeElement.hidden).toBe(true);
            }
        });

        it('should block submit when no user is selected', () => {
            setFormValid();
            setSelectedUsers(undefined);
            checkFormIsInvalid();

            setFormValid();
            setSelectedUsers([]);
            checkFormIsInvalid();
        });

        it('should block when too many users are selected', () => {
            setFormValid();
            setSelectedUsers([{ id: 1 } as UserPublicInfoDTO, { id: 2 } as UserPublicInfoDTO, { id: 3 } as UserPublicInfoDTO]);
            checkFormIsInvalid();
        });

        it('should block when in group mode and no mode is selected', () => {
            if (isChannelDTO(activeConversation)) {
                setFormValid();
                fixture.debugElement.query(By.css('#group')).nativeElement.click();
                fixture.changeDetectorRef.detectChanges();
                expect(component.mode).toBe('group');
                expect(component.isSubmitPossible).toBe(false);
            }
        });

        it('should submit valid form in individual mode', () => {
            setValidIndividualModeFormValues();
            fixture.changeDetectorRef.detectChanges();
            expect(component.form.valid).toBe(true);
            expect(component.isSubmitPossible).toBe(true);
            expect(component.mode).toBe('individual');

            const expectedAddUsersFormData: AddUsersFormData = {
                selectedUsers: [{ id: 1, name: 'test' } as UserPublicInfoDTO],
                addAllStudents: false,
                addAllTutors: false,
                addAllInstructors: false,
            };

            clickSubmitButton(true, expectedAddUsersFormData);
        });

        it('should submit valid form in group mode', () => {
            if (isChannelDTO(activeConversation)) {
                fixture.debugElement.query(By.css('#group')).nativeElement.click();
                fixture.changeDetectorRef.detectChanges();
                setValidGroupModeFormValues();
                fixture.changeDetectorRef.detectChanges();
                expect(component.isSubmitPossible).toBe(true);
                expect(component.mode).toBe('group');

                const expectedAddUsersFormData: AddUsersFormData = {
                    addAllStudents: true,
                    addAllTutors: false,
                    addAllInstructors: false,
                    selectedUsers: [],
                };

                clickSubmitButton(true, expectedAddUsersFormData);
            }
        });

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
            fixture.changeDetectorRef.detectChanges();
            expect(component.form.invalid).toBe(true);
            expect(component.isSubmitPossible).toBe(false);
            clickSubmitButton(false);
        }
        function setFormValid() {
            setValidIndividualModeFormValues();
            fixture.changeDetectorRef.detectChanges();
            expect(component.form.valid).toBe(true);
            expect(component.isSubmitPossible).toBe(true);
        }
        const clickSubmitButton = async (expectSubmitEvent: boolean, expectedFormData?: AddUsersFormData) => {
            const submitFormSpy = vi.spyOn(component, 'submitForm');
            const submitFormEventSpy = vi.spyOn(component.formSubmitted, 'emit');

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
            submitButton.click();

            await fixture.whenStable();
            if (expectSubmitEvent) {
                expect(submitFormSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledWith(expectedFormData);
            } else {
                expect(submitFormSpy).not.toHaveBeenCalled();
                expect(submitFormEventSpy).not.toHaveBeenCalled();
            }
        };
    });
});
