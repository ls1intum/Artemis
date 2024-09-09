import { AfterViewInit, Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, computed, effect, input, signal, viewChild } from '@angular/core';
import { ControlContainer, NgForm, NgModel } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';

@Component({
    selector: 'jhi-title-channel-name',
    templateUrl: './title-channel-name.component.html',
    viewProviders: [{ provide: ControlContainer, useExisting: NgForm }],
})
export class TitleChannelNameComponent implements AfterViewInit, OnDestroy, OnInit {
    @Input() title?: string;
    @Input() channelName?: string;
    @Input() channelNamePrefix: string;
    @Input() titlePattern: string;
    @Input() hideTitleLabel: boolean;
    @Input() emphasizeLabels = false;
    @Input() minTitleLength: number;
    @Input() initChannelName = true;
    hideChannelName = input<boolean>();
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();

    @ViewChild('field_title') field_title: NgModel;
    field_channel_name = viewChild<NgModel>('field_channel_name');

    @Output() titleChange = new EventEmitter<string>();
    @Output() channelNameChange = new EventEmitter<string>();

    formValidSignal = signal<boolean>(false);
    formValid: boolean;
    formValidChanges = new Subject();

    // subscriptions
    fieldTitleSubscription?: Subscription;
    fieldChannelNameSubscription?: Subscription;

    isChannelFieldDisplayed = computed(() => {
        return !this.hideChannelName() && (!this.isEditFieldDisplayedRecord() || this.isEditFieldDisplayedRecord()?.channelName);
    });

    constructor() {
        effect(() => {
            console.log(this.isEditFieldDisplayedRecord()?.channelName);
            this.registerChangeListeners();
        });
    }

    ngAfterViewInit() {
        this.registerChangeListeners();
    }

    ngOnInit(): void {
        if (!this.channelNamePrefix) {
            this.channelNamePrefix = '';
        }

        if (this.initChannelName) {
            // Defer updating the channel name into the next change detection cycle to avoid the
            // "NG0100: Expression has changed after it was checked" error
            setTimeout(() => {
                // Remove trailing hyphens if title is not undefined or empty
                this.formatChannelName(this.channelNamePrefix + (this.title ?? ''), false, !!this.title);
            });
        }
    }

    private registerChangeListeners() {
        this.fieldTitleSubscription = this.field_title.valueChanges?.subscribe(() => this.calculateFormValid());
        this.fieldChannelNameSubscription = this.field_channel_name()?.valueChanges?.subscribe(() => this.calculateFormValid());
    }

    ngOnDestroy() {
        this.fieldTitleSubscription?.unsubscribe();
        this.fieldChannelNameSubscription?.unsubscribe();
    }

    calculateFormValid(): void {
        const updatedFormValidValue = Boolean(this.field_title.valid && (!this.isChannelFieldDisplayed() || this.field_channel_name()?.valid));
        this.formValidSignal.set(updatedFormValidValue);
        this.formValid = updatedFormValidValue;
        this.formValidChanges.next(this.formValid);
    }

    updateTitle(newTitle: string) {
        this.title = newTitle;
        this.titleChange.emit(this.title);
        // Remove trailing hyphens if title is not undefined or empty
        this.formatChannelName(this.channelNamePrefix + this.title, false, !!this.title);
    }

    formatChannelName(newName: string, allowDuplicateHyphens = true, removeTrailingHyphens = false) {
        const specialCharacters = allowDuplicateHyphens ? /[^a-z0-9-]+/g : /[^a-z0-9]+/g;
        const trailingHyphens = removeTrailingHyphens ? /-$/ : new RegExp('[]');
        this.channelName = newName.toLowerCase().replaceAll(specialCharacters, '-').replace(trailingHyphens, '').slice(0, 30);
        this.channelNameChange.emit(this.channelName);
    }
}
