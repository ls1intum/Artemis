import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { TranslateService } from '@ngx-translate/core';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { provideRouter } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('UnitCreationCardComponent', () => {
    let fixture: ComponentFixture<UnitCreationCardComponent>;
    let component: UnitCreationCardComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnitCreationCardComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(UnitCreationCardComponent, {
                remove: { imports: [DocumentationButtonComponent] },
                add: { imports: [MockComponent(DocumentationButtonComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(UnitCreationCardComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const buttonIds = ['createTextUnitButton', 'createExerciseUnitButton', 'createOnlineUnitButton', 'createAttachmentVideoUnitButton'];

    it('should link each kind of content to its creation page', () => {
        fixture.detectChanges();

        const links = buttonIds.map((id) => fixture.nativeElement.querySelector(`a#${id}`) as HTMLAnchorElement | null);
        expect(links.map((link) => link?.getAttribute('href'))).toEqual(['/text-units/create', '/exercise-units/create', '/online-units/create', '/attachment-video-units/create']);
    });

    it.each([
        ['createTextUnitButton', LectureUnitType.TEXT],
        ['createExerciseUnitButton', LectureUnitType.EXERCISE],
        ['createOnlineUnitButton', LectureUnitType.ONLINE],
        ['createAttachmentVideoUnitButton', LectureUnitType.ATTACHMENT_VIDEO],
    ])('should emit the kind for %s when the page opens the form itself', (buttonId, type) => {
        fixture.componentRef.setInput('emitEvents', true);
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.onUnitCreationCardClicked, 'emit');

        (fixture.nativeElement.querySelector(`button#${buttonId}`) as HTMLButtonElement).click();

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(type);
        expect(fixture.nativeElement.querySelector('a')).toBeNull();
    });
});
