import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { faCheck, faChevronDown, faChevronUp, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-two-stage-stepper',
    templateUrl: './two-stage-stepper.component.html',
    styleUrl: './two-stage-stepper.component.scss',
    imports: [FontAwesomeModule, CommonModule],
})
export class TwoStageStepperComponent implements OnInit, OnChanges {
    @Input() currentValue: number = 1;
    @Input() minValue: number = 1;
    @Input() maxValue: number = 16;
    @Input() disabled: boolean = false;
    @Output() valueChange = new EventEmitter<number>();

    tempValue: number = 1;
    isEditing: boolean = false;

    // Icons
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    ngOnInit() {
        this.tempValue = this.currentValue;
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes['currentValue']) {
            if (!this.isEditing) {
                this.tempValue = this.currentValue;
            } else if (changes['currentValue'].currentValue !== changes['currentValue'].previousValue) {
                // If we're editing but the parent value changed (e.g., after successful API call),
                // update our temp value and exit editing mode
                this.tempValue = this.currentValue;
                this.isEditing = false;
            }
        }
    }

    startEditing() {
        if (this.disabled) return;
        this.isEditing = true;
        this.tempValue = this.currentValue;
    }

    increment() {
        if (this.tempValue < this.maxValue) {
            this.tempValue++;
            if (!this.isEditing) {
                this.isEditing = true;
            }
        }
    }

    decrement() {
        if (this.tempValue > this.minValue) {
            this.tempValue--;
            if (!this.isEditing) {
                this.isEditing = true;
            }
        }
    }

    confirm() {
        if (this.tempValue >= this.minValue && this.tempValue <= this.maxValue) {
            this.valueChange.emit(this.tempValue);
            this.isEditing = false;
        }
    }

    cancel() {
        this.tempValue = this.currentValue;
        this.isEditing = false;
    }

    get canIncrement(): boolean {
        return !this.disabled && this.tempValue < this.maxValue;
    }

    get canDecrement(): boolean {
        return !this.disabled && this.tempValue > this.minValue;
    }
}
