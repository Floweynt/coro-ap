package com.floweytf.coro.ap.util.scanners;

import com.floweytf.coro.ap.util.Frame;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeScanner;

public class ContextTrackingTreeScanner extends TreeScanner {
    protected final Frame<Boolean> isSync = new Frame<>();
    protected final Frame<JCClassDecl> currentClass = new Frame<>();

    @Override
    public void visitSynchronized(final JCTree.JCSynchronized tree) {
        isSync.push(true, () -> super.visitSynchronized(tree));
    }

    @Override
    public void visitClassDef(final JCClassDecl tree) {
        Frame.push(
            isSync, currentClass,
            false, tree,
            () -> super.visitClassDef(tree)
        );
    }

    @Override
    public void visitMethodDef(final JCMethodDecl tree) {
        isSync.push(false, () -> super.visitMethodDef(tree));
    }

    @Override
    public void visitLambda(final JCLambda tree) {
        isSync.push(false, () -> super.visitLambda(tree));
    }
}
