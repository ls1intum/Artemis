import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Overlay } from '@angular/cdk/overlay';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { TutorialRegistrationsRegisterSearchBarComponent } from './tutorial-registrations-register-search-bar.component';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

interface TutorialGroupsServiceMock {
    getUnregisteredStudentDTOs: ReturnType<typeof vi.fn>;
}

interface AlertServiceMock {
    addErrorAlert: ReturnType<typeof vi.fn>;
}

interface OverlayRefMock {
    updateSize: ReturnType<typeof vi.fn>;
    attach: ReturnType<typeof vi.fn>;
    dispose: ReturnType<typeof vi.fn>;
}

interface OverlayMock {
    position: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    scrollStrategies: {
        reposition: ReturnType<typeof vi.fn>;
    };
}

describe('TutorialRegistrationsRegisterSearchBarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialRegistrationsRegisterSearchBarComponent;
    let fixture: ComponentFixture<TutorialRegistrationsRegisterSearchBarComponent>;

    let tutorialGroupsServiceMock: TutorialGroupsServiceMock;
    let alertServiceMock: AlertServiceMock;
    let overlayRefMock: OverlayRefMock;
    let overlayMock: OverlayMock;

    const firstStudent: TutorialGroupRegisteredStudentDTO = {
        id: 1,
        name: 'Ada Lovelace',
        login: 'ada',
        email: 'ada@tum.de',
        registrationNumber: 'R001',
        profilePictureUrl: undefined,
    };

    const secondStudent: TutorialGroupRegisteredStudentDTO = {
        id: 2,
        name: 'Alan Turing',
        login: 'alan',
        email: 'alan@tum.de',
        registrationNumber: 'R002',
        profilePictureUrl: undefined,
    };

    beforeEach(async () => {
        tutorialGroupsServiceMock = {
            getUnregisteredStudentDTOs: vi.fn().mockReturnValue(of([])),
        };

        alertServiceMock = {
            addErrorAlert: vi.fn(),
        };

        overlayRefMock = {
            updateSize: vi.fn(),
            attach: vi.fn(),
            dispose: vi.fn(),
        };

        const positionStrategy = {
            withPositions: vi.fn().mockReturnThis(),
            withFlexibleDimensions: vi.fn().mockReturnThis(),
            withPush: vi.fn().mockReturnThis(),
        };

        const connectedPositionBuilder = {
            flexibleConnectedTo: vi.fn().mockReturnValue(positionStrategy),
        };

        overlayMock = {
            position: vi.fn().mockReturnValue(connectedPositionBuilder),
            create: vi.fn().mockReturnValue(overlayRefMock),
            scrollStrategies: {
                reposition: vi.fn().mockReturnValue({}),
            },
        };

        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsRegisterSearchBarComponent],
            providers: [
                { provide: TutorialGroupsService, useValue: tutorialGroupsServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: Overlay, useValue: overlayMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsRegisterSearchBarComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('tutorialGroupId', 11);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load the first page when the search string becomes non-empty', async () => {
        tutorialGroupsServiceMock.getUnregisteredStudentDTOs.mockReturnValue(of([firstStudent, secondStudent]));

        component.searchString.set(' ada ');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupsServiceMock.getUnregisteredStudentDTOs).toHaveBeenCalledOnce();
        expect(tutorialGroupsServiceMock.getUnregisteredStudentDTOs).toHaveBeenCalledWith(7, 11, 'ada', 0, 25);
        expect(component.suggestedStudents()).toEqual([firstStudent, secondStudent]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(1);
        expect(component.hasMorePages()).toBe(false);
        expect(component.firstSuggestedStudentsPageLoading()).toBe(false);
        expect(component.nextSuggestedStudentsPageLoading()).toBe(false);
    });

    it('should show an error alert when loading the first page fails', async () => {
        tutorialGroupsServiceMock.getUnregisteredStudentDTOs.mockReturnValue(throwError(() => new Error('first page failed')));

        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupsServiceMock.getUnregisteredStudentDTOs).toHaveBeenCalledOnce();
        expect(tutorialGroupsServiceMock.getUnregisteredStudentDTOs).toHaveBeenCalledWith(7, 11, 'ada', 0, 25);
        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.registerSearchBar.fetchSuggestionsError');
        expect(component.suggestedStudents()).toEqual([]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(0);
        expect(component.hasMorePages()).toBe(true);
        expect(component.firstSuggestedStudentsPageLoading()).toBe(false);
        expect(component.nextSuggestedStudentsPageLoading()).toBe(false);
    });

    it('should reset suggestion state when the search string is empty', async () => {
        component.suggestedStudents.set([firstStudent]);
        component.nextSuggestedStudentsPageIndex.set(3);
        component.hasMorePages.set(false);
        component.suggestionHighlightIndex.set(0);

        component.searchString.set('  ');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.suggestedStudents()).toEqual([]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(0);
        expect(component.hasMorePages()).toBe(true);
        expect(component.suggestionHighlightIndex()).toBeUndefined();
    });

    it('should mark the input as focused on focus in', () => {
        component.onInputFocusIn();

        expect(component.inputIsFocused()).toBe(true);
    });

    it('should clear the highlight index and mark the input as unfocused on focus out', () => {
        component.suggestionHighlightIndex.set(1);

        component.onInputFocusOut();

        expect(component.suggestionHighlightIndex()).toBeUndefined();
        expect(component.inputIsFocused()).toBe(false);
    });

    it('should emit the selected student and reset the component state', () => {
        const emitSpy = vi.spyOn(component.onStudentSelected, 'emit');

        component.searchString.set('ada');
        component.suggestedStudents.set([firstStudent, secondStudent]);
        component.nextSuggestedStudentsPageIndex.set(4);
        component.hasMorePages.set(false);
        component.suggestionHighlightIndex.set(1);

        component.selectSuggestion(1);

        expect(emitSpy).toHaveBeenCalledWith(secondStudent);
        expect(component.searchString()).toBe('');
        expect(component.suggestedStudents()).toEqual([]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(0);
        expect(component.hasMorePages()).toBe(true);
        expect(component.suggestionHighlightIndex()).toBeUndefined();
    });
});
