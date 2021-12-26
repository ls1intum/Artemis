export const fakeItemTemplate = `
<ng-template #itemTemplate let-item="item"
  let-onCollapseExpand="onCollapseExpand"
  let-onCheckedChange="onCheckedChange">
  <div class="form-check">
    <i *ngIf="item.children" (click)="onCollapseExpand()" aria-hidden="true"
      class="fa" [class.fa-caret-right]="item.collapsed" [class.fa-caret-down]="!item.collapsed"></i>
    <label class="form-check-label">
      <input type="checkbox" class="form-check-input"
        [(ngModel)]="item.checked" (ngModelChange)="onCheckedChange()" [disabled]="item.disabled" />
      {{item.text}}
    </label>
  </div>
</ng-template>
`;
