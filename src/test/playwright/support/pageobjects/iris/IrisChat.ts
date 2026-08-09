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
        return this.page.locator('.p-tab:has(jhi-iris-logo)');
    }

    /** The Iris button of the collapsed icon rail. */
    getCollapsedPanelTab(): Locator {
        return this.page.locator('.collapsed-right-panel-tab:has(jhi-iris-logo)');
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
        return this.page.locator('jhi-llm-selection-modal .modal-backdrop');
    }

    getCloudAiOption(): Locator {
        return this.page.locator('jhi-llm-selection-modal .option-card.cloud-card');
    }

    /**
     * Brings the chat up: expands the panel if it is collapsed, activates the Iris tab, and handles
     * the one-time LLM-selection modal by picking Cloud AI. Waits for the message input, so callers
     * get a chat they can type into. Unlike the floating widget this replaced, the panel stays open
     * after the modal closes, so no second click is needed.
     */
    async openChat(): Promise<void> {
        const railTab = this.getCollapsedPanelTab();
        if (await railTab.count()) {
            await railTab.click();
        }

        const tab = this.getPanelTab();
        await expect(tab).toBeVisible();
        await tab.click();

        // The modal is shown via setTimeout(..., 0), so wait for either it or the input to appear.
        const modal = this.getLlmSelectionModal();
        const messageInput = this.getMessageInput();
        const firstVisible = await Promise.race([
            modal.waitFor({ state: 'visible', timeout: 5000 }).then(() => 'modal' as const),
            messageInput.waitFor({ state: 'visible', timeout: 5000 }).then(() => 'input' as const),
        ]);

        if (firstVisible === 'modal') {
            await this.getCloudAiOption().click();
            await expect(modal).toBeHidden();
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
