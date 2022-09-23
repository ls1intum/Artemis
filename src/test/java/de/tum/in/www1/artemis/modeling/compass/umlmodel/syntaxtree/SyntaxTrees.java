package de.tum.in.www1.artemis.modeling.compass.umlmodel.syntaxtree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public class SyntaxTrees {

    public static final String SYNTAX_TREE_MODEL_1A;

    public static final String SYNTAX_TREE_MODEL_1B;

    public static final String SYNTAX_TREE_MODEL_2;

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
