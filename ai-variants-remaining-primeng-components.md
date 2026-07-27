# Remaining PrimeNG usage in the exercise-variants AI generation feature

Everything else in this feature (dialog, buttons, radio buttons, text inputs, tooltip, popover) has been
migrated to the `tum-ui-*` kit. The following PrimeNG components are kept **only** because the kit does not
have a replacement yet.

| Component                                 | Import                                    | Used in                                                                                                                                                                                | Why kept                                                                                                                 |
| ------------------------------------------ | ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `ConfirmationService` / `<p-confirmdialog>` | `primeng/api`, `primeng/confirmdialog`     | `src/main/webapp/app/core/navbar/variant-generation-tray/variant-generation-tray.component.ts` (cancel-job confirmation)<br>`src/main/webapp/app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component.ts` (cancel-generation confirmation) | The `tum-ui` kit (`src/main/webapp/app/shared-ui/tum-ui/`) has no confirm-dialog component yet — only `tum-ui-dialog`, which is declarative-only and not a drop-in for a programmatic `confirm()` call. |

## Action item

Once the kit gains a confirm-dialog equivalent, swap both usages above and delete this file.

## Note

`agent-chat-modal.component.ts` (atlas competency-mapping chat) also imports PrimeNG (`primeng/button`,
`primeng/checkbox`, `primeng/select`, `primeng/dynamicdialog`), but that component is unrelated to the
exercise-variants feature — this branch only reformatted a couple of unrelated lines in it, it introduces no
new PrimeNG usage, and is out of scope here.
