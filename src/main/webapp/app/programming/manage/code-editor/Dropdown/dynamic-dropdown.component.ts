import { Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { humanizeCategory } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';

@Component({
    selector: 'jhi-dynamic-dropdown',
    templateUrl: './dynamic-dropdown.component.html',
    styleUrl: './dynamic-dropdown.component.scss',
    standalone: true,
    imports: [FormsModule, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, NgbDropdownItem],
})
export class DynamicDropdownComponent {
    issues = input.required<ConsistencyIssue[]>();
    selectedIssue?: ConsistencyIssue;

    selectIssue(issue: ConsistencyIssue) {
        this.selectedIssue = issue;
    }

    getIssueLabel(issue: ConsistencyIssue) {
        return humanizeCategory(issue.category);
    }

    getDropdownLabel() {
        if (!this.shouldBeActive()) {
            return 'No issues available';
        }

        if (!this.selectedIssue) {
            return 'Select issue';
        }

        return this.getIssueLabel(this.selectedIssue);
    }

    shouldBeActive() {
        return this.issues() && this.issues().length > 0;
    }
}
