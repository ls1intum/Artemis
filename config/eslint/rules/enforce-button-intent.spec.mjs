import { describe, it } from 'vitest';
import { createTemplateRuleTester } from './rule-tester.mjs';
import rule from './enforce-button-intent.mjs';

const tester = createTemplateRuleTester();

describe('enforce-button-intent', () => {
    it('checks action families across button APIs without confusing selection, layout or dynamic intent', () => {
        tester.run('enforce-button-intent', rule, {
            valid: [
                '<button tumAetUiButton severity="secondary">Export CSV</button>',
                '<tumaet-ui-button severity="warn"><span jhiTranslate="entity.action.edit"></span></tumaet-ui-button>',
                '<jhi-button title="entity.action.save" />',
                '<jhi-button title="entity.action.download" [btnType]="ButtonType.SECONDARY" />',
                '<button class="btn btn-danger"><span jhiTranslate="entity.action.delete"></span></button>',
                '<button tumAetUiButton severity="danger" data-button-intent="destructive" data-button-intent-reason="Cancelling deletes the persisted assessment">Cancel assessment</button>',
                '<button tumAetUiButton severity="secondary" data-button-intent="utility">Unfold details</button>',
                '<p-button severity="secondary" label="Export CSV" />',
                '<tumaet-ui-button severity="warn" [ariaLabel]="\'entity.action.edit\' | artemisTranslate"><fa-icon /></tumaet-ui-button>',
                '<button tumAetUiButton severity="warn" [attr.aria-label]="\'entity.action.edit\' | artemisTranslate"><fa-icon /></button>',
                '<button tumAetUiButton severity="info">Assessment dashboard</button>',
                '<button tumAetUiButton severity="success">Preview report</button>',
                '<button tumAetUiButton severity="danger" title="Save">Delete</button>',
                "<button tumAetUiButton [severity]=\"editing ? 'warn' : 'primary'\">{{ editing ? 'Edit' : 'Create' }}</button>",
                "<button tumAetUiButton [severity]=\"team() ? 'warn' : 'primary'\">{{ team() ? 'entity.action.edit' : 'entity.action.create' | artemisTranslate }}</button>",
                '<button tumAetUiButton [severity]="saved ? \'primary\' : \'success\'" [disabled]="!saved">Save mappings</button>',
                "<button tumAetUiButton [severity]=\"editing ? 'warn' : 'primary'\">@if (editing) { Edit } @else { Create }</button>",
                "<button tumAetUiButton [severity]=\"editing ? 'warn' : 'primary'\">{{ !editing ? 'Create' : 'Edit' }}</button>",
                "<button tumAetUiButton [severity]=\"editing && ready ? 'warn' : 'primary'\">{{ editing && ready ? 'Edit' : 'Create' }}</button>",
                "<button tumAetUiButton [severity]=\"changed ? 'danger' : 'secondary'\" [attr.data-button-intent]=\"changed ? 'destructive' : 'utility'\" data-button-intent-reason=\"Cancel discards the pending draft; Back only navigates\">{{ changed ? 'Cancel' : 'Back' }}</button>",
                '<button tumAetUiButton severity="secondary" class="flex text-sm border rounded-md">Close</button>',
                '<button tumAetUiButton [severity]="mode()">{{ label() }}</button>',
                '<button tumAetUiButton [severity]="message.saved ? \'success\' : \'primary\'" [disabled]="!ready || message.saved">Save mappings</button>',
                '<button tumAetUiButton role="tab" aria-selected="true">Edit</button>',
                '<button tumAetUiButton severity="primary" data-button-intent="selection" data-button-intent-reason="Chooses editor mode rather than opening an authoring action">Edit</button>',
                '<a href="/download">Download</a>',
                '<div class="text-state-danger">Error status</div>',
            ],
            invalid: [
                { code: '<button tumAetUiButton severity="info">Export CSV</button>', errors: [{ messageId: 'wrongSeverity' }] },
                { code: '<button tumAetUiButton><span jhiTranslate="entity.action.edit"></span></button>', errors: [{ messageId: 'wrongSeverity' }] },
                { code: '<jhi-button title="entity.action.save" [btnType]="ButtonType.SUCCESS" />', errors: [{ messageId: 'wrongSeverity' }] },
                { code: '<button class="btn btn-primary">Download PDF</button>', errors: [{ messageId: 'wrongSeverity' }] },
                { code: '<button tumAetUiButton severity="primary" data-button-intent="transfer">Fetch results</button>', errors: [{ messageId: 'wrongSeverity' }] },
                { code: "<button tumAetUiButton [severity]=\"message.saved ? 'success' : 'primary'\">Save mappings</button>", errors: [{ messageId: 'wrongSeverity' }] },
                {
                    code: '<button tumAetUiButton [severity]="message.saved ? \'success\' : \'primary\'" [disabled]="other.saved">Save mappings</button>',
                    errors: [{ messageId: 'wrongSeverity' }],
                },
                { code: '<button tumAetUiButton severity="danger" data-button-intent="destructive">Cancel</button>', errors: [{ messageId: 'missingReason' }] },
                { code: '<button tumAetUiButton data-button-intent="purple">Proceed</button>', errors: [{ messageId: 'unknownIntent' }] },
                { code: '<button tumAetUiButton [attr.data-button-intent]="intent()">Proceed</button>', errors: [{ messageId: 'dynamicIntent' }] },
                { code: "<button tumAetUiButton [attr.data-button-intent]=\"mode ? 'selection' : 'utility'\">Proceed</button>", errors: [{ messageId: 'unknownIntent' }] },
                { code: '<button tumAetUiButton severity="info" class="text-white">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton style="background: red">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton [style.color]="color()">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton class="btn btn-secondary">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<p-button label="Export CSV" />', errors: [{ messageId: 'wrongSeverity' }] },
                {
                    code: '<tumaet-ui-button severity="warn" [attr.aria-label]="\'entity.action.edit\' | artemisTranslate"><fa-icon /></tumaet-ui-button>',
                    errors: [{ messageId: 'ariaOnHost' }],
                },
                { code: '<button tumAetUiButton severity="primary" title="Save">Delete</button>', errors: [{ messageId: 'wrongSeverity' }] },
                { code: "<button tumAetUiButton [severity]=\"editing ? 'primary' : 'warn'\">{{ editing ? 'Edit' : 'Create' }}</button>", errors: [{ messageId: 'wrongSeverity' }] },
                { code: "<button tumAetUiButton severity=\"primary\">{{ editing ? 'Edit' : 'Create' }}</button>", errors: [{ messageId: 'wrongSeverity' }] },
                { code: '<button tumAetUiButton class="text-red-500">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton class="border-state-danger">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton [ngClass]="{\'text-white\': ready}">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: "<button tumAetUiButton [ngClass]=\"['flex', ready ? 'text-white' : '']\">Details</button>", errors: [{ messageId: 'localColor' }] },
                {
                    code: "<button tumAetUiButton [severity]=\"editing ? 'warn' : 'primary'\">{{ other ? 'Edit' : 'Create' }}</button>",
                    errors: [{ messageId: 'conditionalIntent' }],
                },
                { code: '<button tumAetUiButton style="--tumaet-ui-button-primary-background: red">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton [ngClass]="ready && \'text-white\'">Details</button>', errors: [{ messageId: 'localColor' }] },
                {
                    code: "<button tumAetUiButton [severity]=\"changed ? 'secondary' : 'danger'\" [attr.data-button-intent]=\"changed ? 'destructive' : 'utility'\" data-button-intent-reason=\"Cancel discards pending edits\">{{ changed ? 'Cancel' : 'Back' }}</button>",
                    errors: [{ messageId: 'wrongSeverity' }],
                },
                {
                    code: "<button tumAetUiButton [severity]=\"changed ? 'danger' : 'secondary'\" [attr.data-button-intent]=\"other ? 'destructive' : 'utility'\" data-button-intent-reason=\"Cancel discards pending edits\">{{ changed ? 'Cancel' : 'Back' }}</button>",
                    errors: [{ messageId: 'conditionalIntent' }],
                },
                {
                    code: "<button tumAetUiButton [severity]=\"changed ? 'danger' : 'secondary'\" [attr.data-button-intent]=\"changed ? 'destructive' : 'utility'\">{{ changed ? 'Cancel' : 'Back' }}</button>",
                    errors: [{ messageId: 'missingReason' }],
                },
                { code: '<button tumAetUiButton [class.text-white]="selected">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton [style]="{color: foreground()}">Details</button>', errors: [{ messageId: 'localColor' }] },
                { code: '<button tumAetUiButton data-button-intent="transfer" [severity]="computedSeverity()">Fetch data</button>', errors: [{ messageId: 'dynamicSeverity' }] },
            ],
        });
    });
});
