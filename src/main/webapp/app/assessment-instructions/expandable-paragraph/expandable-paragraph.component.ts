import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-expandable-paragraph',
    templateUrl: './expandable-paragraph.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableParagraphComponent implements OnInit {
    @Input() header = 'Toggle paragraph';
    @Input() text: any;
    @Input() isCollapsed = false;
    constructor() {}

    ngOnInit() {}
}
