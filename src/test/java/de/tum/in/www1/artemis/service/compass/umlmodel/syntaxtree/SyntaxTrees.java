package de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

class SyntaxTrees {

    static final String SYNTAX_TREE_MODEL_1A;

    static final String SYNTAX_TREE_MODEL_1B;

    static final String SYNTAX_TREE_MODEL_2;

    static {
        try {
            SYNTAX_TREE_MODEL_1A = IOUtils.toString(SyntaxTrees.class.getResource("syntaxTreeModel1a.json"), StandardCharsets.UTF_8);
            SYNTAX_TREE_MODEL_1B = IOUtils.toString(SyntaxTrees.class.getResource("syntaxTreeModel1b.json"), StandardCharsets.UTF_8);
            SYNTAX_TREE_MODEL_2 = IOUtils.toString(SyntaxTrees.class.getResource("syntaxTreeModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SyntaxTrees() {
        // do not instantiate
    }
}
