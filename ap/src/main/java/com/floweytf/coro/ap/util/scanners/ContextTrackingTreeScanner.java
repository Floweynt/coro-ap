package com.floweytf.coro.ap.util.scanners;

import com.floweytf.coro.ap.util.Frame;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

public class ContextTrackingTreeScanner extends TreeScanner {
    protected final Frame<Boolean> isSync = new Frame<>();
    protected final Frame<JCTree.JCClassDecl> currentClass = new Frame<>();
    protected final Frame<Integer> lambdaCount = new Frame<>();

    @Override
    public void visitSynchronized(final JCTree.JCSynchronized tree) {
        isSync.push(true, () -> super.visitSynchronized(tree));
    }

    @Override
    public void visitClassDef(final JCTree.JCClassDecl tree) {
        Frame.push(
            isSync, currentClass, lambdaCount,
            false, tree, 0,
            () -> super.visitClassDef(tree)
        );
    }

    @Override
    public void visitMethodDef(final JCTree.JCMethodDecl tree) {
        isSync.push(false, () -> super.visitMethodDef(tree));
    }

    @Override
    public void visitLambda(final JCTree.JCLambda tree) {
        lambdaCount.apply(s -> s + 1);
        isSync.push(false, () -> super.visitLambda(tree));
    }
}
