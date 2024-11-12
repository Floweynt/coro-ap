package com.floweytf.coro.ap.util;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;

public class JavacUtils {
    public static Symbol.ModuleSymbol getModule(Symbol.ClassSymbol symbol) {
        return symbol.owner.kind == Kinds.Kind.MDL ?
            (Symbol.ModuleSymbol) symbol.owner :
            symbol.packge().modle;
    }
}
