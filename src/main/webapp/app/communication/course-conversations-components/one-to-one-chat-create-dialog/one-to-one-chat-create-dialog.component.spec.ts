import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations-components/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('OneToOneChatCreateDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: OneToOneChatCreateDialogComponent;
    let fixture: ComponentFixture<OneToOneChatCreateDialogComponent>;
    const course = { id: 1 } as Course;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                ReactiveFormsModule,
                OneToOneChatCreateDialogComponent,
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
        fixture = TestBed.createComponent(OneToOneChatCreateDialogComponent);
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

    it('should dismiss modal if cancel is selected', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const destroySpy = vi.spyOn(dialogRef, 'destroy');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        expect(destroySpy).toHaveBeenCalledOnce();
    });

    it('should close the dialog with the selected user once one is selected', () => {
        const selectedUser = { id: 1 };
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.onSelectedUsersChange([selectedUser]);
        fixture.changeDetectorRef.detectChanges();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(selectedUser);
    });
});
