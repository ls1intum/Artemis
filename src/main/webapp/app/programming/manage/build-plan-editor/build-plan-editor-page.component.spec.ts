import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
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
});
