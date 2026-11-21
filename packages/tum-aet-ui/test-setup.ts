/**
 * jsdom does not implement cascade layers and ignores every rule inside `@layer`. The styles of the package are all in
 * the layer `tumaet-ui`, so a spec that reads a computed style would see none of them. This setup turns the layer of
 * each stylesheet that a test adds to the document into a block that jsdom understands.
 */
const PACKAGE_LAYER = /@layer tumaet-ui\s*\{/g;

function withoutPackageLayer<T extends Node>(node: T): T {
    if (node instanceof HTMLStyleElement && node.textContent?.includes('@layer tumaet-ui')) {
        node.textContent = node.textContent.replace(PACKAGE_LAYER, '@media all {');
    }
    return node;
}

const appendChild = Node.prototype.appendChild;
Node.prototype.appendChild = function <T extends Node>(this: Node, node: T): T {
    return appendChild.call(this, withoutPackageLayer(node)) as T;
};

const insertBefore = Node.prototype.insertBefore;
Node.prototype.insertBefore = function <T extends Node>(this: Node, node: T, child: Node | null): T {
    return insertBefore.call(this, withoutPackageLayer(node), child) as T;
};
