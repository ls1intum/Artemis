import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OverlayContainer } from '@angular/cdk/overlay';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { TutorialRegistrationsRegisterSearchBarComponent } from './tutorial-registrations-register-search-bar.component';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

interface TutorialGroupApiServiceMock {
    searchUnregisteredStudents: ReturnType<typeof vi.fn>;
}

interface AlertServiceMock {
    addErrorAlert: ReturnType<typeof vi.fn>;
}

function assertNonNullable<T>(value: T): asserts value is NonNullable<T> {
    expect(value).not.toBeNull();
    expect(value).not.toBeUndefined();
}

function createStudent(id: number): TutorialGroupStudent {
    return {
        id,
        login: `student${id}`,
        name: `Student ${id}`,
        email: `student${id}@tum.de`,
        registrationNumber: `R${id}`,
        profilePictureUrl: undefined,
    };
}

function createPageOfStudents(): TutorialGroupStudent[] {
    return Array.from({ length: 25 }, (_, index) => createStudent(index + 1));
}

function simulateViewportScrollNearBottom(viewport: HTMLElement) {
    Object.defineProperty(viewport, 'scrollHeight', { configurable: true, value: 400 });
    Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 100 });
    Object.defineProperty(viewport, 'scrollTop', { configurable: true, value: 280, writable: true });
    viewport.dispatchEvent(new Event('scroll'));
}

describe('TutorialRegistrationsRegisterSearchBarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialRegistrationsRegisterSearchBarComponent;
    let fixture: ComponentFixture<TutorialRegistrationsRegisterSearchBarComponent>;

    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;
    let alertServiceMock: AlertServiceMock;
    let overlayContainer: OverlayContainer;

    const firstStudent: TutorialGroupStudent = {
        id: 1,
        name: 'Ada Lovelace',
        login: 'ada',
        email: 'ada@tum.de',
        registrationNumber: 'R001',
        profilePictureUrl: undefined,
    };

    const secondStudent: TutorialGroupStudent = {
        id: 2,
        name: 'Alan Turing',
        login: 'alan',
        email: 'alan@tum.de',
        registrationNumber: 'R002',
        profilePictureUrl: undefined,
    };

    beforeEach(async () => {
        tutorialGroupApiServiceMock = {
            searchUnregisteredStudents: vi.fn().mockReturnValue(of([])),
        };

        alertServiceMock = {
            addErrorAlert: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsRegisterSearchBarComponent],
            providers: [
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        overlayContainer = TestBed.inject(OverlayContainer);
        fixture = TestBed.createComponent(TutorialRegistrationsRegisterSearchBarComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('tutorialGroupId', 11);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        overlayContainer.getContainerElement().innerHTML = '';
    });

    it('should load the first page when the search string becomes non-empty', async () => {
        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValue(of([firstStudent, secondStudent]));

        component.searchString.set(' ada ');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenCalledWith(7, 11, 'ada', 0, 25);
        expect(component.suggestedStudents()).toEqual([firstStudent, secondStudent]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(1);
        expect(component.hasMorePages()).toBe(false);
        expect(component.firstSuggestedStudentsPageLoading()).toBe(false);
        expect(component.nextSuggestedStudentsPageLoading()).toBe(false);
    });

    it('should show an error alert when loading the first page fails', async () => {
        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValue(throwError(() => new Error('first page failed')));

        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenCalledWith(7, 11, 'ada', 0, 25);
        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.registerSearchBar.fetchSuggestionsError');
        expect(component.suggestedStudents()).toEqual([]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(0);
        expect(component.hasMorePages()).toBe(true);
        expect(component.firstSuggestedStudentsPageLoading()).toBe(false);
        expect(component.nextSuggestedStudentsPageLoading()).toBe(false);
    });

    it('should load the next page when the suggestions viewport is scrolled near the bottom', async () => {
        const firstPageStudents = createPageOfStudents();
        const nextPageStudent: TutorialGroupStudent = {
            id: 99,
            login: 'grace',
            name: 'Grace Hopper',
            email: 'grace@tum.de',
            registrationNumber: 'R099',
            profilePictureUrl: undefined,
        };

        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValueOnce(of(firstPageStudents)).mockReturnValueOnce(of([nextPageStudent]));

        const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
        input.dispatchEvent(new FocusEvent('focusin'));
        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        const viewport = overlayContainer.getContainerElement().querySelector('.search-viewport') as HTMLElement | null;
        assertNonNullable(viewport);

        simulateViewportScrollNearBottom(viewport);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenCalledTimes(2);
        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenNthCalledWith(2, 7, 11, 'ada', 1, 25);
        expect(component.suggestedStudents()).toEqual([...firstPageStudents, nextPageStudent]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(2);
        expect(component.hasMorePages()).toBe(false);
        expect(component.firstSuggestedStudentsPageLoading()).toBe(false);
        expect(component.nextSuggestedStudentsPageLoading()).toBe(false);
    });

    it('should show an error alert when next page loading fails', async () => {
        const firstPageStudents = createPageOfStudents();

        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValueOnce(of(firstPageStudents)).mockReturnValueOnce(throwError(() => new Error('next page failed')));

        const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
        input.dispatchEvent(new FocusEvent('focusin'));
        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        const viewport = overlayContainer.getContainerElement().querySelector('.search-viewport') as HTMLElement | null;
        assertNonNullable(viewport);

        simulateViewportScrollNearBottom(viewport);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenCalledTimes(2);
        expect(tutorialGroupApiServiceMock.searchUnregisteredStudents).toHaveBeenNthCalledWith(2, 7, 11, 'ada', 1, 25);
        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.registerSearchBar.fetchSuggestionsError');
        expect(component.suggestedStudents()).toEqual(firstPageStudents);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(1);
        expect(component.hasMorePages()).toBe(true);
        expect(component.firstSuggestedStudentsPageLoading()).toBe(false);
        expect(component.nextSuggestedStudentsPageLoading()).toBe(false);
    });

    it('should reset suggestion state when the search string is empty', async () => {
        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValue(of([firstStudent, secondStudent]));

        const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
        input.dispatchEvent(new FocusEvent('focusin'));
        component.searchString.set('  ada');
        fixture.detectChanges();
        await fixture.whenStable();

        component.searchString.set('  ');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.suggestedStudents()).toEqual([]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(0);
        expect(component.hasMorePages()).toBe(true);
        expect(component.suggestionHighlightIndex()).toBeUndefined();
    });

    it('should close the panel on focus out and reopen it on focus in when suggestions exist', async () => {
        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValue(of([firstStudent, secondStudent]));

        const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;

        input.dispatchEvent(new FocusEvent('focusin'));
        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        const viewport = overlayContainer.getContainerElement().querySelector('.search-viewport') as HTMLElement | null;
        assertNonNullable(viewport);
        Object.defineProperty(viewport, 'scrollTo', { configurable: true, value: vi.fn() });

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();

        expect(component.inputIsFocused()).toBe(true);
        expect(component.suggestionHighlightIndex()).toBe(0);
        expect(overlayContainer.getContainerElement().querySelector('.search-panel')).not.toBeNull();

        input.dispatchEvent(new FocusEvent('focusout'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.inputIsFocused()).toBe(false);
        expect(component.suggestionHighlightIndex()).toBeUndefined();
        expect(overlayContainer.getContainerElement().querySelector('.search-panel')).toBeNull();

        input.dispatchEvent(new FocusEvent('focusin'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.inputIsFocused()).toBe(true);
        expect(overlayContainer.getContainerElement().querySelector('.search-panel')).not.toBeNull();
    });

    it('should update the highlight with arrow keys and emit the selected student on enter after loading suggestions', async () => {
        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValue(of([firstStudent, secondStudent]));
        const emitSpy = vi.spyOn(component.onStudentSelected, 'emit');
        const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;

        input.dispatchEvent(new FocusEvent('focusin'));
        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        const viewport = overlayContainer.getContainerElement().querySelector('.search-viewport') as HTMLElement | null;
        assertNonNullable(viewport);
        Object.defineProperty(viewport, 'scrollTo', { configurable: true, value: vi.fn() });

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        expect(component.suggestionHighlightIndex()).toBe(0);

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        expect(component.suggestionHighlightIndex()).toBe(1);

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        fixture.detectChanges();
        expect(component.suggestionHighlightIndex()).toBe(0);

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        expect(component.suggestionHighlightIndex()).toBe(1);

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledWith(secondStudent);
        expect(component.searchString()).toBe('');
        expect(component.suggestedStudents()).toEqual([]);
        expect(component.nextSuggestedStudentsPageIndex()).toBe(0);
        expect(component.hasMorePages()).toBe(true);
        expect(component.suggestionHighlightIndex()).toBeUndefined();
    });

    it('should select the first suggestion when arrow down is pressed without an active selection', async () => {
        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValue(of([firstStudent, secondStudent]));
        const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;

        input.dispatchEvent(new FocusEvent('focusin'));
        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        const viewport = overlayContainer.getContainerElement().querySelector('.search-viewport') as HTMLElement | null;
        assertNonNullable(viewport);
        Object.defineProperty(viewport, 'scrollTo', { configurable: true, value: vi.fn() });

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();

        expect(component.suggestionHighlightIndex()).toBe(0);
    });

    it('should keep the selection undefined when arrow up is pressed without an active selection', async () => {
        tutorialGroupApiServiceMock.searchUnregisteredStudents.mockReturnValue(of([firstStudent, secondStudent]));
        const input = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;

        input.dispatchEvent(new FocusEvent('focusin'));
        component.searchString.set('ada');
        fixture.detectChanges();
        await fixture.whenStable();

        const viewport = overlayContainer.getContainerElement().querySelector('.search-viewport') as HTMLElement | null;
        assertNonNullable(viewport);
        Object.defineProperty(viewport, 'scrollTo', { configurable: true, value: vi.fn() });

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        fixture.detectChanges();

        expect(component.suggestionHighlightIndex()).toBeUndefined();
    });
});
