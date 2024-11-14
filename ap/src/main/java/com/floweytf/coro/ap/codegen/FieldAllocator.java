package com.floweytf.coro.ap.codegen;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

public class FieldAllocator {
    private final Map<Type, IntList> pool = new Object2ObjectOpenHashMap<>();
    private int allocFieldId = 0;

    public int getOrAllocateFieldId(Type type, Object2IntMap<Type> map) {
        final var index = map.getOrDefault(type, 0);
        final var fields = pool.computeIfAbsent(type, ignored -> new IntArrayList());

        if (fields.size() <= index) {
            fields.add(allocFieldId++);
        }

        map.put(type, index + 1);
        return fields.getInt(index);
    }

    public void codegen(List<FieldNode> fields, int access) {
        pool.forEach((type, integers) -> {
            integers.forEach(i -> {
                fields.add(new FieldNode(access, "l" + i, type.getDescriptor(), null, null));
            });
        });
    }
}
