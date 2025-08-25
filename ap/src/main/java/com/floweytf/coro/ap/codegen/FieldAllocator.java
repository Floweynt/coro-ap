package com.floweytf.coro.ap.codegen;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

/**
 * Name allocator for fields, used for generating the fields required for the saving/loading stack & LVT state on
 * coroutine suspend/resume.
 */
public class FieldAllocator {
    private final Map<Type, IntList> pool = new Object2ObjectOpenHashMap<>();
    private int allocFieldId;

    /**
     * Allocates a field ID for a specific type.
     *
     * @param type       The type of the variable.
     * @param contextMap The context, used to keep track of number of variables per type.
     * @return The allocated id.
     */
    public int getOrAllocateFieldId(final Type type, final Object2IntMap<Type> contextMap) {
        final var index = contextMap.getOrDefault(type, 0);
        final var fields = pool.computeIfAbsent(type, ignored -> new IntArrayList());

        // need to allocate
        if (fields.size() <= index) {
            fields.add(allocFieldId++);
        }

        contextMap.put(type, index + 1);
        return fields.getInt(index);
    }

    /**
     * Emits fields to a ClassNode.
     *
     * @param fields The fields list of a {@code ClassNode}
     * @param access The access flags to use for each field.
     */
    public void generateFields(final List<FieldNode> fields, final int access) {
        for (final var entry : pool.entrySet()) {
            final var type = entry.getKey();
            for (final var i : entry.getValue()) {
                fields.add(new FieldNode(access, "l" + i, type.getDescriptor(), null, null));
            }
        }
    }
}
