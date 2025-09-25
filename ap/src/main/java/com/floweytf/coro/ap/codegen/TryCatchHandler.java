package com.floweytf.coro.ap.codegen;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

// we need to implement some non-trivial logic in regard to exception handler blocks
// specifically, each try block that suspends must be broken apart, so f.e. a block like
//
// try (L1, L2) -> L3
// L1
//     ...
//     Co.await(...);
//     ...
// L2
//
// L3: /* catch block */
//
// must become
// try (L1, Suspend) -> L3, (Resume, L2) -> L3
//
// L1
//     ...
// Suspend
//     suspend
//     return
// ResumeEnter
//     /* resume */
// Resume
//     ...
// L2
class TryCatchHandler {
    private class TryCatchHelper extends TryCatchBlockNode {
        private final LabelNode unclonedEndNode;

        private TryCatchHelper(final TryCatchBlockNode node, final LabelCloner labelCloner) {
            super(
                labelCloner.get(node.start),
                labelCloner.get(node.end),
                labelCloner.get(node.handler),
                node.type
            );
            this.unclonedEndNode = node.end;
            this.invisibleTypeAnnotations = node.invisibleTypeAnnotations;
            this.visibleTypeAnnotations = node.visibleTypeAnnotations;
        }

        private void splitBlock(final LabelNode startNode, final LabelNode endNode) {
            final var outputNode = new TryCatchBlockNode(
                start,
                startNode,
                handler,
                type
            );

            outputNode.invisibleTypeAnnotations = invisibleTypeAnnotations;
            outputNode.visibleTypeAnnotations = visibleTypeAnnotations;

            implMethod.tryCatchBlocks.add(outputNode);

            // update start node
            start = endNode;
        }
    }

    private final MethodNode implMethod;

    // note: both maps are indexed by the pre-cloned label
    private final Map<LabelNode, List<TryCatchHelper>> byStartLabel = new HashMap<>();
    // endLabel -> { blocks }
    private final Map<LabelNode, Set<TryCatchHelper>> activeLabels = new HashMap<>();

    TryCatchHandler(final MethodNode implMethod, final MethodNode originalMethod, final LabelCloner labelCloner) {
        this.implMethod = implMethod;

        if (originalMethod.tryCatchBlocks != null) {
            for (final var tryCatchBlock : originalMethod.tryCatchBlocks) {
                byStartLabel.computeIfAbsent(tryCatchBlock.start, ignored -> new ArrayList<>())
                    .add(new TryCatchHelper(tryCatchBlock, labelCloner));
            }
        }
    }

    public void onLabelNode(final LabelNode node) {
        final var enteredBlocks = byStartLabel.get(node);
        final var exitedBlocks = activeLabels.get(node);

        if (enteredBlocks != null) {
            for (final var enteredBlock : enteredBlocks) {
                activeLabels.computeIfAbsent(enteredBlock.unclonedEndNode, ignored -> new ReferenceOpenHashSet<>())
                    .add(enteredBlock);
            }
        }

        if (exitedBlocks != null) {
            implMethod.tryCatchBlocks.addAll(exitedBlocks);
            activeLabels.remove(node);
        }
    }

    public void splitTryCatchBlocks(final LabelNode start, final LabelNode end) {
        activeLabels.forEach((labelNode, tryCatchHelpers) -> {
            for (final var tryCatchHelper : tryCatchHelpers) {
                tryCatchHelper.splitBlock(start, end);
            }
        });
    }
}
