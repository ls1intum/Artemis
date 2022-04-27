import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-complaints-textarea-counter',
    templateUrl: './complaints-textarea-counter.component.html',
    providers: [],
})
export class ComplaintsTextAreaCounterComponent {
    @Input() maxLength: number;
    @Input() content?: string;
    @Input() visible?: boolean;
}
