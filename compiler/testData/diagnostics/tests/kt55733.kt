// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

fun main() {
    val appendable = <!NO_COMPANION_OBJECT!>Appendable<!>
    val stringBuilder = <!NO_COMPANION_OBJECT!>StringBuilder<!>
    val exception = <!NO_COMPANION_OBJECT!>IllegalStateException<!>
}

/* GENERATED_FIR_TAGS: functionDeclaration, localProperty, propertyDeclaration */
