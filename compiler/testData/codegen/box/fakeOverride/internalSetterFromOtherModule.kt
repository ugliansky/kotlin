// ISSUE: KT-91437

// MODULE: a
// FILE: a.kt

open class A {
    open var foo = 0; internal set
}

// MODULE: main(a)
// FILE: main.kt

import kotlin.test.assertEquals

class B : A() {
    override var foo = 42
}

fun box(): String {
    assertEquals(0, A().foo)
    assertEquals(42, B().foo)
    return "OK"
}
