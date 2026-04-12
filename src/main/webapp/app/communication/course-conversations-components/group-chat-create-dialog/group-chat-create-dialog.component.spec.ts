import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GroupChatCreateDialogComponent } from 'app/communication/course-conversations-components/group-chat-create-dialog/group-chat-create-dialog.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GroupChatCreateDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GroupChatCreateDialogComponent;
    let fixture: ComponentFixture<GroupChatCreateDialogComponent>;
    const course = { id: 1 };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                ReactiveFormsModule,
                GroupChatCreateDialogComponent,
                MockComponent(CourseUsersSelectorComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: DynamicDialogRef, useValue: { close: vi.fn(), destroy: vi.fn(), onClose: new Subject() } },
                { provide: DynamicDialogConfig, useValue: { data: {} } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(GroupChatCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        initializeDialog(component, fixture, {
            course,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.isInitialized).toBe(true);
    });

    it('should close the dialog with the selected users', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const selectedUsers = [{ id: 1 }, { id: 2 }];
        component.selectedUsersControl!.setValue(selectedUsers);
        fixture.changeDetectorRef.detectChanges();
        const submitButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(selectedUsers);
    });

    it('should dismiss modal if cancel is selected', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const destroySpy = vi.spyOn(dialogRef, 'destroy');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        expect(destroySpy).toHaveBeenCalledOnce();
    });
});
