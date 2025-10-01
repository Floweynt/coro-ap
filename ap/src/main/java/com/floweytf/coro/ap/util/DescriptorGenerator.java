package com.floweytf.coro.ap.util;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Name;

public class DescriptorGenerator extends Types.SignatureGenerator {
    private final StringBuilder sb = new StringBuilder();

    public DescriptorGenerator(final Types types) {
        super(types);
    }

    @Override
    protected void append(final char ch) {
        sb.append(ch);
    }

    @Override
    protected void append(final byte[] ba) {
        sb.append(new String(ba));
    }

    @Override
    protected void append(final Name name) {
        sb.append(name.toString());
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public String generate(final Type type) {
        sb.setLength(0);
        assembleSig(type);
        return sb.toString();
    }
}
