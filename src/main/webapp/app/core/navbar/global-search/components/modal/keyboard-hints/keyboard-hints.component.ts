import { Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-global-search-keyboard-hints',
    standalone: true,
    imports: [ArtemisTranslatePipe],
    templateUrl: './keyboard-hints.component.html',
})
export class KeyboardHintsComponent {}
