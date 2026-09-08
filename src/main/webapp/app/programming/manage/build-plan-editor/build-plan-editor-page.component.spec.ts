import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { MockComponent } from 'ng-mocks';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { BuildPlanEditorPageComponent } from 'app/programming/manage/build-plan-editor/build-plan-editor-page.component';
import { BuildPlanEditorComponent } from 'app/programming/manage/build-plan-editor/build-plan-editor.component';
import { LocalCIBuildPlanEditorComponent } from 'app/programming/manage/build-plan-editor/localci-build-plan-editor.component';

describe('BuildPlanEditorPageComponent', () => {
    function createFixture(localCIActive: boolean): ComponentFixture<BuildPlanEditorPageComponent> {
        TestBed.configureTestingModule({
            imports: [BuildPlanEditorPageComponent],
            providers: [{ provide: ProfileService, useValue: { isProfileActive: () => localCIActive } }],
        });
        // Replace the two heavy child editors with lightweight mocks so branching can be asserted via the DOM.
        TestBed.overrideComponent(BuildPlanEditorPageComponent, {
            remove: { imports: [BuildPlanEditorComponent, LocalCIBuildPlanEditorComponent] },
            add: { imports: [MockComponent(BuildPlanEditorComponent), MockComponent(LocalCIBuildPlanEditorComponent)] },
        });
        const fixture = TestBed.createComponent(BuildPlanEditorPageComponent);
        fixture.detectChanges();
        return fixture;
    }

    it('should render the structured LocalCI editor when LocalCI is active', () => {
        const fixture = createFixture(true);

        expect(fixture.componentInstance.isLocalCIActive()).toBe(true);
        expect(fixture.nativeElement.querySelector('jhi-localci-build-plan-editor')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-build-plan-editor')).toBeNull();
    });

    it('should render the script-based editor when LocalCI is not active', () => {
        const fixture = createFixture(false);

        expect(fixture.componentInstance.isLocalCIActive()).toBe(false);
        expect(fixture.nativeElement.querySelector('jhi-build-plan-editor')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-localci-build-plan-editor')).toBeNull();
    });

    it('should delegate the unsaved-changes check to the active LocalCI editor', () => {
        const fixture = createFixture(true);
        const editor = fixture.debugElement.query(By.directive(LocalCIBuildPlanEditorComponent)).componentInstance as LocalCIBuildPlanEditorComponent;
        const canDeactivateStub = vi.spyOn(editor, 'canDeactivate').mockReturnValue(false);

        expect(fixture.componentInstance.canDeactivate()).toBe(false);

        canDeactivateStub.mockReturnValue(true);
        expect(fixture.componentInstance.canDeactivate()).toBe(true);
    });

    it('should allow leaving when the external CI editor is shown', () => {
        const fixture = createFixture(false);

        // the external CI editor keeps no editable state here, so there is nothing to guard
        expect(fixture.componentInstance.canDeactivate()).toBe(true);
    });
});
