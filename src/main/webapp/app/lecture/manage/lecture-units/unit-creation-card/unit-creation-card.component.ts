import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconDefinition, faCheck, faFileVideo, faLink, faScroll } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';

interface UnitCreationOption {
    type: LectureUnitType;
    buttonId: string;
    route: string;
    icon: IconDefinition;
    label: string;
}

/** The buttons that add one content item of a kind, either by opening its form on the page or by navigating to its creation page. */
@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
    imports: [RouterLink, FaIconComponent, TumAetUiButtonDirective, TranslateDirective, DocumentationButtonComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UnitCreationCardComponent {
    protected readonly documentationType: DocumentationType = 'Units';

    /** Emits the chosen kind instead of navigating, for a page that opens the form itself. */
    readonly emitEvents = input<boolean>(false);

    readonly onUnitCreationCardClicked = output<LectureUnitType>();

    protected readonly options: UnitCreationOption[] = [
        { type: LectureUnitType.TEXT, buttonId: 'createTextUnitButton', route: 'text-units', icon: faScroll, label: 'artemisApp.lectureUnit.unitCreationCard.text' },
        { type: LectureUnitType.EXERCISE, buttonId: 'createExerciseUnitButton', route: 'exercise-units', icon: faCheck, label: 'artemisApp.lectureUnit.unitCreationCard.exercise' },
        { type: LectureUnitType.ONLINE, buttonId: 'createOnlineUnitButton', route: 'online-units', icon: faLink, label: 'artemisApp.lectureUnit.unitCreationCard.online' },
        {
            type: LectureUnitType.ATTACHMENT_VIDEO,
            buttonId: 'createAttachmentVideoUnitButton',
            route: 'attachment-video-units',
            icon: faFileVideo,
            label: 'artemisApp.lectureUnit.unitCreationCard.attachmentVideo',
        },
    ];

    onButtonClicked(type: LectureUnitType) {
        this.onUnitCreationCardClicked.emit(type);
    }
}
