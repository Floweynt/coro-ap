package com.floweytf.coro.ap.codegen;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

/**
 * A handler for try-catch blocks in coroutines.
 *
 * <p>
 * This class is required because we need to implement some non-trivial logic in regard to exception handler blocks.
 * Specifically, each try block that suspends must be broken apart, so f.e. a block like
 * </p>
 *
 * <pre>{@code
 * // TRY L1, L2 CATCH L3
 * L1:
 *     ...
 *     Co.await(...);
 *     ...
 * L2:
 *     ...
 * L3: (catch block)
 * }</pre>
 *
 * <p>
 * Must be broken apart when suspending/resuming, since even though it is not possible for an exception to be thrown
 * when resuming the coroutine, the JVM will still complain. Therefore, we can only begin catching *after* the
 * resumption has completed. Therefore, we need to transform this into:
 * </p>
 *
 * <pre>{@code
 * // TRY L1, L_SUSPEND CATCH L3
 * // TRY L_RESUME, L2 CATCH L3
 * L1:
 *     ...
 * L_SUSPEND:
 *     // suspend
 *     return
 * L_RESUME_ENTER:
 *     // resume
 * RESUME:
 *     // check and throw exception
 *     ...
 * L2:
 * }</pre>
 */
class TryCatchHandler {
    private static class IndexedTryCatchBlock extends TryCatchBlockNode {
        private final int index;

        public IndexedTryCatchBlock(
            final LabelNode start, final LabelNode end, final LabelNode handler,
            final String type, final int index
        ) {
            super(start, end, handler, type);
            this.index = index;
        }

        public int index() {
            return index;
        }
    }

    private class TryCatchHelper extends IndexedTryCatchBlock {
        private final LabelNode unclonedEndNode;

        private TryCatchHelper(final TryCatchBlockNode node, final LabelCloner labelCloner, final int index) {
            super(
                labelCloner.get(node.start),
                labelCloner.get(node.end),
                labelCloner.get(node.handler),
                node.type,
                index
            );
            this.unclonedEndNode = node.end;
            this.invisibleTypeAnnotations = node.invisibleTypeAnnotations;
            this.visibleTypeAnnotations = node.visibleTypeAnnotations;
        }

        private void splitBlock(final LabelNode startNode, final LabelNode endNode) {
            final var outputNode = new IndexedTryCatchBlock(
                start,
                startNode,
                handler,
                type,
                index()
            );

            outputNode.invisibleTypeAnnotations = invisibleTypeAnnotations;
            outputNode.visibleTypeAnnotations = visibleTypeAnnotations;

            tryCatchBlocks.add(outputNode);

            // update start node
            start = endNode;
        }
    }

    private final MethodNode implMethod;

    private final List<IndexedTryCatchBlock> tryCatchBlocks = new ArrayList<>();

    // note: both maps are indexed by the pre-cloned label
    private final Map<LabelNode, List<TryCatchHelper>> byStartLabel = new HashMap<>();
    // endLabel -> { blocks }
    private final Map<LabelNode, Set<TryCatchHelper>> activeLabels = new HashMap<>();

    TryCatchHandler(final MethodNode implMethod, final MethodNode originalMethod, final LabelCloner labelCloner) {
        this.implMethod = implMethod;

        if (originalMethod.tryCatchBlocks != null) {
            for (int i = 0; i < originalMethod.tryCatchBlocks.size(); i++) {
                final var tryCatchBlock = originalMethod.tryCatchBlocks.get(i);
                byStartLabel.computeIfAbsent(tryCatchBlock.start, ignored -> new ArrayList<>())
                    .add(new TryCatchHelper(tryCatchBlock, labelCloner, i));
            }
        }
    }

    void onLabelNode(final LabelNode node) {
        final var enteredBlocks = byStartLabel.get(node);
        final var exitedBlocks = activeLabels.get(node);

        if (enteredBlocks != null) {
            for (final var enteredBlock : enteredBlocks) {
                activeLabels.computeIfAbsent(enteredBlock.unclonedEndNode, ignored -> new ReferenceOpenHashSet<>())
                    .add(enteredBlock);
            }
        }

        if (exitedBlocks != null) {
            tryCatchBlocks.addAll(exitedBlocks);
            activeLabels.remove(node);
        }
    }

    void onFinished() {
        tryCatchBlocks.sort(Comparator.comparingInt(IndexedTryCatchBlock::index));
        implMethod.tryCatchBlocks = new ArrayList<>(tryCatchBlocks);
    }

    /**
     * Splits apart a catch block.
     *
     * @param start The start of the no-except region.
     * @param end   The end of the no-except region.
     */
    void splitTryCatchBlocks(final LabelNode start, final LabelNode end) {
        activeLabels.forEach((labelNode, tryCatchHelpers) -> {
            for (final var tryCatchHelper : tryCatchHelpers) {
                tryCatchHelper.splitBlock(start, end);
            }
        });
    }
}
