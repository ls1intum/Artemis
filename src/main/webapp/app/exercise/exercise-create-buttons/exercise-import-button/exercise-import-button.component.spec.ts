import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseImportButtonComponent } from './exercise-import-button.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { IconDefinition, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { Subject } from 'rxjs';

describe('ExerciseImportButtonComponent', () => {
    let component: ExerciseImportButtonComponent;
    let fixture: ComponentFixture<ExerciseImportButtonComponent>;
    let dialogService: DialogService;
    let router: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, ExerciseImportButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportButtonComponent);
        component = fixture.componentInstance;
        dialogService = TestBed.inject(DialogService);
        fixture.componentRef.setInput('course', { id: 123 });
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        router = TestBed.inject(Router);
        fixture.detectChanges();
    });

    it.each([
        { exerciseType: ExerciseType.MODELING, id: 2, expectedComponent: ExerciseImportComponent },
        { exerciseType: ExerciseType.TEXT, id: 2, expectedComponent: ExerciseImportComponent },
        { exerciseType: ExerciseType.FILE_UPLOAD, id: 2, expectedComponent: ExerciseImportComponent },
        { exerciseType: ExerciseType.PROGRAMMING, id: 2, expectedComponent: ExerciseImportTabsComponent },
        { exerciseType: ExerciseType.PROGRAMMING, id: undefined, expectedComponent: ExerciseImportTabsComponent },
    ])('should open import modal', async ({ exerciseType, id, expectedComponent }: { exerciseType: ExerciseType; id?: number; expectedComponent: any }) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);

        const onCloseSubject = new Subject<Exercise | undefined>();
        const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
        const openSpy = jest.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const routerSpy = jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
        const beforeNavigateSpy = jest.spyOn(component.beforeNavigate, 'emit');

        component.openImportModal();

        expect(beforeNavigateSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(
            expectedComponent,
            expect.objectContaining({
                data: { exerciseType },
            }),
        );

        // Simulate dialog closing with result
        onCloseSubject.next({ id, maxPoints: 1 } as Exercise);
        onCloseSubject.complete();

        // Wait for async operations
        await fixture.whenStable();

        if (id && exerciseType === ExerciseType.PROGRAMMING) {
            expect(routerSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 123, `${exerciseType}-exercises`, 'import', 2]);
        } else if (!id && exerciseType === ExerciseType.PROGRAMMING) {
            expect(routerSpy).toHaveBeenCalledWith(['/course-management', 123, 'programming-exercises', 'import-from-file'], {
                state: { programmingExerciseForImportFromFile: { id: undefined, maxPoints: 1 } },
            });
        } else {
            expect(routerSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 123, `${exerciseType}-exercises`, 2, 'import']);
        }
    });

    it.each([
        { exerciseType: ExerciseType.MODELING, expectedIcon: faProjectDiagram, expectedTranslationLabel: 'artemisApp.modelingExercise.home.importLabel' },
        { exerciseType: ExerciseType.FILE_UPLOAD, expectedIcon: faFileUpload, expectedTranslationLabel: 'artemisApp.fileUploadExercise.home.importLabel' },
        { exerciseType: ExerciseType.TEXT, expectedIcon: faFont, expectedTranslationLabel: 'artemisApp.textExercise.home.importLabel' },
        { exerciseType: ExerciseType.PROGRAMMING, expectedIcon: faKeyboard, expectedTranslationLabel: 'artemisApp.programmingExercise.home.importLabel' },
    ])(
        'should determine correct translation key and icon',
        ({ exerciseType, expectedIcon, expectedTranslationLabel }: { exerciseType: ExerciseType; expectedIcon: IconDefinition; expectedTranslationLabel: string }) => {
            fixture.componentRef.setInput('exerciseType', exerciseType);
            component.ngOnInit();
            expect(component.icon).toEqual(expectedIcon);
            expect(component.translationLabel).toEqual(expectedTranslationLabel);
        },
    );
});
