package com.floweytf.coro.ap.codegen;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.objectweb.asm.tree.LabelNode;

class LabelCloner extends Object2ObjectOpenHashMap<LabelNode, LabelNode> {
    @Override
    public LabelNode get(final Object k) {
        var res = super.get(k);
        if (res == null) {
            res = new LabelNode();
            put((LabelNode) k, res);
        }
        return res;
    }
}
