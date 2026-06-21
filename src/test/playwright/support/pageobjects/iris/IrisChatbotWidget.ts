import { Locator, Page, expect } from '@playwright/test';

/**
 * Page object for the floating Iris chatbot widget and the embedded lecture Iris chat.
 *
 * The floating FAB is `jhi-exercise-chatbot-button .chatbot-button jhi-iris-logo`; clicking it
 * opens the widget overlay `.chat-widget`. The widget header `.chat-header` exposes
 * `button.header-control` controls whose fa icons are `circle-info` (info), `expand`/`compress`
 * (maximize/restore), and `xmark` (close). Maximizing resizes `.chat-widget` to ~93% of the
 * `.cdk-overlay-container` width via an inline pixel `style.width`.
 */
export class IrisChatbotWidget {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /** The floating Iris FAB (only present when Iris is enabled for the course). */
    getFab(): Locator {
        return this.page.locator('jhi-exercise-chatbot-button .chatbot-button jhi-iris-logo');
    }

    /** The open widget overlay container. */
    getWidget(): Locator {
        return this.page.locator('.chat-widget');
    }

    getHeader(): Locator {
        return this.page.locator('.chat-header');
    }

    getInfoControl(): Locator {
        return this.page.locator('.chat-header .header-control:has(.fa-circle-info)');
    }

    getMaximizeControl(): Locator {
        return this.page.locator('.chat-header .header-control:has(.fa-expand)');
    }

    getRestoreControl(): Locator {
        return this.page.locator('.chat-header .header-control:has(.fa-compress)');
    }

    getCloseControl(): Locator {
        return this.page.locator('.chat-header .header-control:has(.fa-xmark)');
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
     * The "Choose Your AI Experience" LLM-selection modal. On the FIRST time a user opens
     * Iris (before any LLM-usage decision is stored), Artemis shows this modal and blocks
     * the chat until the user picks an option. Selecting "Cloud" persists CLOUD_AI (which
     * is what the mock-LLM stack is wired for) and closes the modal (and the chat dialog).
     */
    getLlmSelectionModal(): Locator {
        return this.page.locator('jhi-llm-selection-modal .modal-backdrop');
    }

    getCloudAiOption(): Locator {
        return this.page.locator('jhi-llm-selection-modal .option-card.cloud-card');
    }

    /**
     * Opens the widget by a real pointer click on the FAB and waits for the chat to render.
     *
     * Handles the one-time LLM-selection modal: if it appears, it picks Cloud AI (real
     * pointer click), which closes both the modal and the chat dialog, then re-clicks the
     * FAB so the chat opens directly. Subsequent opens go straight to the chat. Waits for
     * the message input so callers can interact with a fully-rendered widget.
     */
    async openWidget(): Promise<void> {
        const fab = this.getFab();
        await expect(fab).toBeVisible();
        await fab.click();

        // First open may surface the AI-selection modal; choose Cloud and reopen.
        if (
            await this.getLlmSelectionModal()
                .isVisible()
                .catch(() => false)
        ) {
            await this.getCloudAiOption().click();
            await expect(this.getLlmSelectionModal()).toBeHidden();
            await expect(fab).toBeVisible();
            await fab.click();
        }

        await expect(this.getWidget()).toBeVisible();
        await expect(this.getMessageInput()).toBeVisible();
    }

    /** Returns the width of the CDK overlay container (the widget's positioning context). */
    async getOverlayWidth(): Promise<number> {
        const box = await this.page.locator('.cdk-overlay-container').boundingBox();
        expect(box, { message: 'cdk-overlay-container should have a bounding box' }).not.toBeNull();
        return box!.width;
    }

    async getWidgetWidth(): Promise<number> {
        const box = await this.getWidget().boundingBox();
        expect(box, { message: 'chat-widget should have a bounding box' }).not.toBeNull();
        return box!.width;
    }

    /** Sends a chat message via the textarea and the send button (real pointer click). */
    async sendMessage(text: string): Promise<void> {
        const input = this.getMessageInput();
        await expect(input).toBeVisible();
        await input.fill(text);
        await this.getSendButton().click();
    }
}
