import { Locator, Page, expect } from '@playwright/test';

/**
 * Page object for the embedded Iris chat, the panel that sits beside the content on the lecture and
 * exercise pages (`jhi-resizable-panels`).
 *
 * The panel is addressed through its tab: while the right side is expanded the tabs are `.p-tab`
 * elements, and the Iris one is the tab carrying `jhi-iris-logo`; while it is collapsed the same
 * panels are buttons in `.collapsed-right-panel`. The chat itself is the shared base chatbot, so the
 * input (`.chat-input textarea`), the send button (`#irisSendButton`) and the assistant messages
 * (`.llm-message-wrapper`) are the same elements in every host.
 */
export class IrisChat {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /** The Iris tab of the expanded panel (only present when Iris is enabled for the course). */
    getPanelTab(): Locator {
        return this.page.getByTestId('resizable-panel-tab').filter({ has: this.page.locator('jhi-iris-logo') });
    }

    /** The Iris button of the collapsed icon rail. */
    getCollapsedPanelTab(): Locator {
        return this.page.getByTestId('collapsed-panel-tab').filter({ has: this.page.locator('jhi-iris-logo') });
    }

    /** Collapses the panel back to the icon rail. */
    getCollapseControl(): Locator {
        return this.page.locator('.right-panel-collapse-button');
    }

    getChat(): Locator {
        return this.page.locator('jhi-iris-base-chatbot');
    }

    getMessageInput(): Locator {
        return this.page.locator('.chat-input textarea');
    }

    getSendButton(): Locator {
        return this.page.locator('#irisSendButton');
    }

    getLlmMessages(): Locator {
        return this.page.locator('.llm-message-wrapper');
    }

    /**
     * The "Choose Your AI Experience" LLM-selection modal. The first time a user opens Iris (before
     * any LLM-usage decision is stored), Artemis shows this modal and blocks the chat until the user
     * picks an option. Selecting "Cloud" persists CLOUD_AI, which is what the mock-LLM stack is
     * wired for, and closes the modal.
     */
    getLlmSelectionModal(): Locator {
        return this.page.getByTestId('llm-selection-modal');
    }

    getCloudAiOption(): Locator {
        return this.page.getByTestId('llm-selection-cloud-option');
    }

    /**
     * Answers the LLM-selection modal with Cloud AI if it is up within the given budget, and reports whether it was.
     */
    private async acceptCloudAiIfAsked(timeout: number): Promise<boolean> {
        const modal = this.getLlmSelectionModal();
        try {
            await modal.waitFor({ state: 'visible', timeout });
        } catch {
            return false;
        }
        await this.getCloudAiOption().click();
        await expect(modal).toBeHidden();
        return true;
    }

    /**
     * Brings the chat up: expands the panel if it is collapsed, activates the Iris tab, and handles
     * the one-time LLM-selection modal by picking Cloud AI. Waits for the message input, so callers
     * get a chat they can type into. Unlike the floating widget this replaced, the panel stays open
     * after the modal closes, so no second click is needed.
     */
    async openChat(): Promise<void> {
        // For a user who has never made the LLM-usage choice the modal is already up when the page finishes loading,
        // and its backdrop swallows the panel clicks below. So answer it first rather than only afterwards; doing it
        // the other way round left the tab click retrying until the test timed out.
        const answeredBeforeOpening = await this.acceptCloudAiIfAsked(3000);

        const railTab = this.getCollapsedPanelTab();
        if (await railTab.count()) {
            await railTab.click();
        }

        const tab = this.getPanelTab();
        await expect(tab).toBeVisible();
        await tab.click();

        const messageInput = this.getMessageInput();
        if (!answeredBeforeOpening) {
            // It can also be opened when the chat itself mounts, via setTimeout(..., 0)
            const modal = this.getLlmSelectionModal();
            const firstVisible = await Promise.race([
                modal.waitFor({ state: 'visible', timeout: 5000 }).then(() => 'modal' as const),
                messageInput.waitFor({ state: 'visible', timeout: 5000 }).then(() => 'input' as const),
            ]);

            if (firstVisible === 'modal') {
                await this.getCloudAiOption().click();
                await expect(modal).toBeHidden();
            }
        }

        await expect(this.getChat()).toBeVisible();
        await expect(messageInput).toBeVisible();
    }

    /** Sends a chat message via the textarea and the send button (real pointer click). */
    async sendMessage(text: string): Promise<void> {
        const input = this.getMessageInput();
        await expect(input).toBeVisible();
        await input.fill(text);
        await this.getSendButton().click();
    }
}
