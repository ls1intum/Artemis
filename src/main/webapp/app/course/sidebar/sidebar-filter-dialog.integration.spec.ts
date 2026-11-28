import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogService } from '@tumaet/ui-angular/dialog';
import { SidebarComponent } from './sidebar.component';
import { SidebarCardElement, SidebarData } from 'app/foundation/types/sidebar';
import { ExerciseFilterOptions } from 'app/foundation/types/exercise-filter';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Sidebar exercise filter with the real dialog service', () => {
    afterEach(() => TestBed.inject(TumAetUiDialogService).closeAll());

    it('initializes available filters before rendering and applies the selection to the sidebar', async () => {
        await TestBed.configureTestingModule({
            imports: [SidebarComponent],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: Router, useValue: new MockRouter() },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(SidebarComponent, { set: { template: '' } })
            .compileComponents();

        const fixture = TestBed.createComponent(SidebarComponent);
        const sidebar = fixture.componentInstance;
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        const textExercise = new TextExercise(undefined, undefined);
        const programmingCard: SidebarCardElement = { id: 1, title: 'Programming', size: 'M', exercise: programmingExercise };
        const textCard: SidebarCardElement = { id: 2, title: 'Text', size: 'M', exercise: textExercise };
        const sidebarData: SidebarData = {
            sidebarType: 'exercise',
            groupByCategory: false,
            groupedData: {
                exercises: { entityData: [programmingCard, textCard] },
            },
            ungroupedData: [programmingCard, textCard],
        };
        const exerciseFilters: ExerciseFilterOptions = {
            exerciseTypesFilter: {
                isDisplayed: true,
                options: [ExerciseType.PROGRAMMING, ExerciseType.TEXT].map((value) => ({ name: value, value, checked: false, icon: getIcon(value) })),
            },
        };
        fixture.componentRef.setInput('sidebarData', sidebarData);
        fixture.componentRef.setInput('collapseState', {});
        fixture.componentRef.setInput('sidebarItemAlwaysShow', {});
        fixture.detectChanges();
        sidebar.exerciseFilters.set(exerciseFilters);

        sidebar.openFilterExercisesDialog();
        await fixture.whenStable();

        const dialog = document.querySelector('[role="dialog"]')!;
        const typeSelection = dialog.querySelector('#exercise-type-filter-selection');
        expect(typeSelection).not.toBeNull();
        const checkboxes = typeSelection!.querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
        expect(checkboxes).toHaveLength(2);
        const apply = dialog.querySelector<HTMLButtonElement>('button[jhiTranslate="artemisApp.courseOverview.exerciseFilter.applyFilter"]')!;
        expect(apply.disabled).toBe(false);

        checkboxes[0].click();
        await fixture.whenStable();
        expect(exerciseFilters.exerciseTypesFilter!.options[0].checked).toBe(false);
        apply.click();
        await fixture.whenStable();

        expect(sidebar.sidebarDataInternal().groupedData!.exercises.entityData).toEqual([programmingCard]);
        expect(sidebar.sidebarDataInternal().ungroupedData).toEqual([programmingCard]);
        expect(sidebar.exerciseFilters()!.exerciseTypesFilter!.options[0].checked).toBe(true);
        expect(sidebar.isFilterActive()).toBe(true);
        expect(sidebarData.ungroupedData).toHaveLength(2);
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });
});
