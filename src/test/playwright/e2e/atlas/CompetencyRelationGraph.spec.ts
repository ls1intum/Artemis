import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/course/shared/entities/course.model';
import { expect, Locator, Page } from '@playwright/test';
import { Competency } from 'app/atlas/shared/entities/competency.model';

/**
 * End-to-end coverage for the in-house DAG graph component (`jhi-dag-graph`, PR #12903) in its two
 * real consumers: the course-competency relation graph and the learning-path analytics graph.
 *
 * Scope note on stability: the graph's pan / wheel-zoom / minimap interactions are intentionally NOT
 * driven here. Simulating wheel and pointer drags over a thin-stroke SVG is timing- and
 * coordinate-sensitive and is a classic source of e2e flakiness; that logic is covered deterministically
 * by the component unit tests (dag-graph.component.spec.ts). These e2e tests assert the things that are
 * stable end-to-end: that the graph renders the right nodes/edges/labels against real seeded data, that
 * it reacts to data changes, and that clicking an edge selects the relation.
 */
test.describe('Competency relation graph (in-house DAG graph)', { tag: '@fast' }, () => {
    let course: Course;
    let variables: Competency;
    let loops: Competency;
    let functions: Competency;
    let recursion: Competency;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ courseName: 'Relation Graph Course' });
        // Learning paths are needed for the analytics-graph test; harmless for the relation-graph tests.
        await courseManagementAPIRequests.enableLearningPaths(course);

        variables = await courseManagementAPIRequests.createCompetency(course, 'Variables');
        loops = await courseManagementAPIRequests.createCompetency(course, 'Loops');
        functions = await courseManagementAPIRequests.createCompetency(course, 'Functions');
        recursion = await courseManagementAPIRequests.createCompetency(course, 'Recursion');

        // One relation of each type so all three edge labels are exercised.
        // createCompetencyRelation(course, tailId, headId, type); the UI renders ASSUMES as "Requires".
        await courseManagementAPIRequests.createCompetencyRelation(course, loops.id!, variables.id!, 'EXTENDS');
        await courseManagementAPIRequests.createCompetencyRelation(course, functions.id!, variables.id!, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, recursion.id!, functions.id!, 'MATCHES');
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    /**
     * Opens the "Edit relations" modal and waits until the graph has finished measuring its nodes.
     * The component keeps `.graph-content` at `visibility: hidden` until every node reported a size,
     * so waiting for it to be visible is the stable signal that the layout is ready to assert on.
     */
    async function openRelationGraph(page: Page): Promise<Locator> {
        await page.getByRole('button', { name: 'Edit relations' }).click();
        const graph = page.locator('jhi-course-competencies-relation-graph jhi-dag-graph');
        await expect(graph).toBeVisible();
        await expect(graph.locator('.graph-content')).toBeVisible();
        return graph;
    }

    test('renders all competencies, typed relations and a minimap with the in-house component', async ({ page, competencyManagement }) => {
        await competencyManagement.goto(course.id!);
        const graph = await openRelationGraph(page);

        // Migrated away from @swimlane/ngx-graph entirely.
        await expect(page.locator('ngx-graph')).toHaveCount(0);

        // One node per competency, one edge per relation.
        await expect(graph.locator('.nodes .node-group')).toHaveCount(4);
        await expect(graph.locator('.edges g.edge')).toHaveCount(3);

        // Nodes are labelled with the competency titles.
        for (const title of ['Variables', 'Loops', 'Functions', 'Recursion']) {
            await expect(graph.getByText(title, { exact: true })).toBeVisible();
        }

        // Each relation type renders as an edge label (ASSUMES is shown as REQUIRES).
        for (const label of ['EXTENDS', 'MATCHES', 'REQUIRES']) {
            await expect(graph).toContainText(label);
        }

        // The minimap and its viewport indicator render.
        await expect(graph.locator('.minimap')).toBeVisible();
        await expect(graph.locator('.minimap .minimap-viewport')).toHaveCount(1);
    });

    test('selects a relation when its edge is clicked', async ({ page, competencyManagement }) => {
        await competencyManagement.goto(course.id!);
        const graph = await openRelationGraph(page);

        const selectedEdges = graph.locator('.edges g.edge path.line.selected');
        await expect(selectedEdges).toHaveCount(0);

        // Dispatch the click on the edge group: a real .click() on a thin curved SVG path is
        // hit-test-flaky, while dispatchEvent deterministically exercises the (click) handler.
        await graph.locator('.edges g.edge').first().dispatchEvent('click');

        await expect(selectedEdges).toHaveCount(1);
    });

    test('updates the graph when a relation is created and deleted', async ({ page, competencyManagement }) => {
        await competencyManagement.goto(course.id!);
        const graph = await openRelationGraph(page);
        const edges = graph.locator('.edges g.edge');
        await expect(edges).toHaveCount(3);

        const isRelationsRequest = (method: string) => (response: { url(): string; request(): { method(): string } }) =>
            response.url().includes(`/courses/${course.id}/course-competencies/relations`) && response.request().method() === method;

        // Create "Functions MATCHES Loops": two sibling competencies with no path between them, so the
        // client's circular-dependency guard never blocks the button (a deterministic, non-cyclic relation).
        await page.selectOption('#head', { label: 'Functions' });
        await page.selectOption('#tail', { label: 'Loops' });
        await page.selectOption('#type', 'MATCHES');
        const createButton = page.getByRole('button', { name: 'Create Relation' });
        // The button stays disabled until all three signals are set; waiting for it removes the select→click race.
        await expect(createButton).toBeEnabled();
        const createResponse = page.waitForResponse(isRelationsRequest('POST'));
        await createButton.click();
        await createResponse;

        // The graph reacts to the new relation: a fourth edge appears.
        await expect(edges).toHaveCount(4);

        // Selecting the same head/tail surfaces the existing relation; deleting it removes the edge again.
        await page.selectOption('#head', { label: 'Functions' });
        await page.selectOption('#tail', { label: 'Loops' });
        const deleteButton = page.getByRole('button', { name: 'Delete relation' });
        await expect(deleteButton).toBeVisible();
        const deleteResponse = page.waitForResponse(isRelationsRequest('DELETE'));
        await deleteButton.click();
        await deleteResponse;
        await expect(edges).toHaveCount(3);
    });

    test('renders the aggregated competency graph on the learning-path analytics page', async ({ page }) => {
        await page.goto(`/course-management/${course.id}/learning-path-management`);
        const graph = page.locator('jhi-competency-graph jhi-dag-graph');
        await expect(graph).toBeVisible({ timeout: 30000 });
        await expect(graph.locator('.graph-content')).toBeVisible();

        await expect(page.locator('ngx-graph')).toHaveCount(0);
        await expect(graph.locator('.nodes .node-group')).toHaveCount(4);
        await expect(graph.locator('.edges g.edge')).toHaveCount(3);
        await expect(graph.locator('.minimap')).toBeVisible();
    });
});
