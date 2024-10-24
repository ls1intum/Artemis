import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Posting } from 'app/entities/metis/posting.model';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { PostingDirective } from 'app/shared/metis/posting.directive';

class MockPosting implements Posting {
    content: string;

    constructor(content: string) {
        this.content = content;
    }
}

class MockReactionsBar {
    editPosting = jest.fn();
    togglePin = jest.fn();
    deletePosting = jest.fn();
    checkIfPinned = jest.fn().mockReturnValue(DisplayPriority.NONE);
    selectReaction = jest.fn();
}

@Component({
    template: `<div jhiPosting></div>`,
})
class TestPostingComponent extends PostingDirective<MockPosting> {
    reactionsBar: MockReactionsBar = new MockReactionsBar();

    get reactionsBarInstance() {
        return this.reactionsBar;
    }

    get reactionsBarGetter() {
        return this.reactionsBar;
    }
}

describe('PostingDirective', () => {
    let component: TestPostingComponent;
    let fixture: ComponentFixture<TestPostingComponent>;
    let mockReactionsBar: MockReactionsBar;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TestPostingComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TestPostingComponent);
        component = fixture.componentInstance;
        mockReactionsBar = new MockReactionsBar();
        component.reactionsBar = mockReactionsBar;
        component.posting = new MockPosting('Test content');
        component.isCommunicationPage = false;
        component.isThreadSidebar = false;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize content on ngOnInit', () => {
        component.ngOnInit();
        expect(component.content).toBe('Test content');
    });

    it('should call editPosting on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.editPosting();
        expect(mockReactionsBar.editPosting).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
    });

    it('should call togglePin on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.togglePin();
        expect(mockReactionsBar.togglePin).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
    });

    it('should call deletePosting on reactionsBar and hide dropdown', () => {
        component.showDropdown = true;
        component.deletePost();
        expect(mockReactionsBar.deletePosting).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
    });

    it('should return display priority from reactionsBar', () => {
        const priority = component.checkIfPinned();
        expect(mockReactionsBar.checkIfPinned).toHaveBeenCalled();
        expect(priority).toBe(DisplayPriority.NONE);
    });

    it('should call selectReaction on reactionsBar and hide reaction selector', () => {
        const event = { reaction: 'like' };
        component.showReactionSelector = true;
        component.selectReaction(event);
        expect(mockReactionsBar.selectReaction).toHaveBeenCalledWith(event);
        expect(component.showReactionSelector).toBeFalse();
    });

    it('should add reaction and set click position', () => {
        const mouseEvent = new MouseEvent('click', {
            clientX: 100,
            clientY: 200,
        });
        const preventDefaultSpy = jest.spyOn(mouseEvent, 'preventDefault');
        component.addReaction(mouseEvent);
        expect(preventDefaultSpy).toHaveBeenCalled();
        expect(component.showDropdown).toBeFalse();
        expect(component.clickPosition).toEqual({ x: 100, y: 200 });
        expect(component.showReactionSelector).toBeTrue();
    });

    it('should toggle reaction selector visibility', () => {
        component.showReactionSelector = false;
        component.toggleEmojiSelect();
        expect(component.showReactionSelector).toBeTrue();

        component.toggleEmojiSelect();
        expect(component.showReactionSelector).toBeFalse();
    });
});
