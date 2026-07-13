import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { ExerciseAddModalComponent } from 'app/course/manage/exercises/create-modal/exercise-add-modal.component';
import { IMPORT_DIALOG_BACK } from 'app/course/manage/exercises/create-modal/import-dialog-footer.component';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ExerciseAddModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseAddModalComponent>;
    let component: ExerciseAddModalComponent;
    let profileService: ProfileService;
    let featureToggleService: MockFeatureToggleService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseAddModalComponent],
            providers: [
                provideRouter([]),
                provideNoopAnimations(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        }).compileComponents();

        profileService = TestBed.inject(ProfileService);
        featureToggleService = TestBed.inject(FeatureToggleService) as unknown as MockFeatureToggleService;
    });

    /** The gating is resolved at construction (toSignal + computed), so configure the mocks before creating the component. */
    function createComponent(): void {
        fixture = TestBed.createComponent(ExerciseAddModalComponent);
        component = fixture.componentInstance;
    }

    function visibleCardTypes(): ExerciseType[] {
        // exerciseTypeCards is a protected computed signal; read it directly for the gating assertion.
        return (component as unknown as { exerciseTypeCards: () => { type: ExerciseType }[] }).exerciseTypeCards().map((card) => card.type);
    }

    it('shows every exercise type when all module features and the programming toggle are active', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        createComponent();
        expect(visibleCardTypes()).toEqual([ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD]);
    });

    it('hides text, modeling and file-upload when their module feature is inactive', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);
        createComponent();
        // Quiz is not module-gated and programming stays because its feature toggle defaults to active.
        expect(visibleCardTypes()).toEqual([ExerciseType.PROGRAMMING, ExerciseType.QUIZ]);
    });

    it('hides programming while its feature toggle is off', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        featureToggleService.setFeatureToggleState(FeatureToggle.ProgrammingExercises, false);
        createComponent();
        expect(visibleCardTypes()).not.toContain(ExerciseType.PROGRAMMING);
    });

    describe('tabs and visibility', () => {
        beforeEach(() => {
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
            createComponent();
        });

        it('activates the tab matching the mode input whenever the modal becomes visible', () => {
            fixture.componentRef.setInput('mode', 'import');
            fixture.componentRef.setInput('visible', true);
            fixture.detectChanges();
            expect(component.activeTab()).toBe('import');
        });

        it('keeps the current tab for the unified mode', () => {
            component.setActiveTab('export');
            fixture.componentRef.setInput('mode', 'unified');
            fixture.componentRef.setInput('visible', true);
            fixture.detectChanges();
            expect(component.activeTab()).toBe('export');
        });

        it('emits visibleChange(false) on close', () => {
            const emitted: boolean[] = [];
            component.visibleChange.subscribe((v) => emitted.push(v));
            component.close();
            expect(emitted).toEqual([false]);
        });

        it('renders the translated dialog header', () => {
            fixture.componentRef.setInput('visible', true);
            fixture.detectChanges();
            // The dialog is appended to the body, so it is not reachable from the fixture element.
            expect(document.body.querySelector('.p-dialog-title')?.textContent).toBe('artemisApp.exerciseManagement.addModal.header');
        });
    });

    describe('navigateToCreate', () => {
        let router: Router;
        let navigateSpy: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
            createComponent();
            router = TestBed.inject(Router);
            navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        });

        it('navigates to the type-specific create route and closes', () => {
            fixture.componentRef.setInput('courseId', 42);
            const emitted: boolean[] = [];
            component.visibleChange.subscribe((v) => emitted.push(v));
            const card = (component as unknown as { exerciseTypeCards: () => { type: ExerciseType; routeSegment: string }[] })
                .exerciseTypeCards()
                .find((c) => c.type === ExerciseType.QUIZ)!;

            component.navigateToCreate(card as never);

            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 42, 'quiz-exercises', 'new']);
            expect(emitted).toEqual([false]);
        });

        it('does not navigate without a course id but still closes', () => {
            const emitted: boolean[] = [];
            component.visibleChange.subscribe((v) => emitted.push(v));
            const card = (component as unknown as { exerciseTypeCards: () => { type: ExerciseType }[] }).exerciseTypeCards()[0];

            component.navigateToCreate(card as never);

            expect(navigateSpy).not.toHaveBeenCalled();
            expect(emitted).toEqual([false]);
        });
    });

    describe('startImport', () => {
        let router: Router;
        let navigateSpy: ReturnType<typeof vi.spyOn>;
        let dialogService: DialogService;
        let onClose: Subject<Exercise | string | undefined>;

        beforeEach(() => {
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
            createComponent();
            router = TestBed.inject(Router);
            navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            dialogService = TestBed.inject(DialogService);
            onClose = new Subject();
            vi.spyOn(dialogService, 'open').mockReturnValue({ onClose: onClose.asObservable() } as unknown as DynamicDialogRef);
            fixture.componentRef.setInput('courseId', 42);
        });

        it('opens the tabbed import dialog for programming and the plain list for other types', () => {
            component.startImport(ExerciseType.PROGRAMMING);
            expect(dialogService.open).toHaveBeenLastCalledWith(
                ExerciseImportTabsComponent,
                expect.objectContaining({ data: expect.objectContaining({ exerciseType: ExerciseType.PROGRAMMING }) }),
            );

            component.startImport(ExerciseType.TEXT);
            expect(dialogService.open).toHaveBeenLastCalledWith(
                ExerciseImportComponent,
                expect.objectContaining({ data: expect.objectContaining({ exerciseType: ExerciseType.TEXT }) }),
            );
        });

        it('reopens the modal on the import tab when the dialog closes with the back sentinel', () => {
            const emitted: boolean[] = [];
            component.visibleChange.subscribe((v) => emitted.push(v));

            component.startImport(ExerciseType.TEXT);
            onClose.next(IMPORT_DIALOG_BACK);

            // close() on open, then the reopen with true.
            expect(emitted).toEqual([false, true]);
            expect(component.activeTab()).toBe('import');
        });

        it('navigates to the import route for a selected non-programming exercise', () => {
            component.startImport(ExerciseType.TEXT);
            onClose.next({ id: 7 } as Exercise);

            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 42, 'text-exercises', 7, 'import']);
        });

        it('navigates to the programming import route for a selected programming exercise', () => {
            component.startImport(ExerciseType.PROGRAMMING);
            onClose.next({ id: 7 } as Exercise);

            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 42, 'programming-exercises', 'import', 7]);
        });

        it('routes a from-file programming import (no id) to the import-from-file page with state', () => {
            const fromFile = { title: 'from file' } as Exercise;
            component.startImport(ExerciseType.PROGRAMMING);
            onClose.next(fromFile);

            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 42, 'programming-exercises', 'import-from-file'], {
                state: { programmingExerciseForImportFromFile: fromFile },
            });
        });

        it('does nothing when the dialog is dismissed without a result', () => {
            component.startImport(ExerciseType.TEXT);
            onClose.next(undefined);
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('does not navigate when the course id is missing', () => {
            fixture.componentRef.setInput('courseId', undefined);
            component.startImport(ExerciseType.TEXT);
            onClose.next({ id: 7 } as Exercise);
            expect(navigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('export and group creation', () => {
        beforeEach(() => {
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
            createComponent();
        });

        it('emits exportRequested and closes', () => {
            const exported = vi.fn();
            const emitted: boolean[] = [];
            component.exportRequested.subscribe(exported);
            component.visibleChange.subscribe((v) => emitted.push(v));

            component.requestExport();

            expect(exported).toHaveBeenCalledOnce();
            expect(emitted).toEqual([false]);
        });

        it('emits groupCreate and closes', () => {
            const created = vi.fn();
            const emitted: boolean[] = [];
            component.groupCreate.subscribe(created);
            component.visibleChange.subscribe((v) => emitted.push(v));

            component.createGroup();

            expect(created).toHaveBeenCalledOnce();
            expect(emitted).toEqual([false]);
        });
    });
});
