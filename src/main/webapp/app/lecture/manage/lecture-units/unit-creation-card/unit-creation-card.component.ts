import { Component, input, output } from '@angular/core';
import { faCheck, faFileVideo, faLink, faScroll } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, DocumentationButtonComponent, ArtemisTranslatePipe],
})
export class UnitCreationCardComponent {
    readonly documentationType: DocumentationType = 'Units';

    emitEvents = input<boolean>(false);

    onUnitCreationCardClicked = output<LectureUnitType>();

    unitType = LectureUnitType;

    // Icons
    faCheck = faCheck;
    faScroll = faScroll;
    faLink = faLink;
    faFileVideo = faFileVideo;

    onButtonClicked(type: LectureUnitType) {
        if (this.emitEvents()) {
            this.onUnitCreationCardClicked.emit(type);
            return;
        }
    }
}
