package com.aegida.core.model

/**
 * The ten pictures Mom picks from (storyboard, Setup board). Her pick is shown on every genuine Aegida
 * screen, so a fake one without it is easy to spot. The drawings are in :app; the [label] is the plain
 * word spoken by a screen reader and shown under the choice.
 */
enum class SafetyPicture(val label: String) {
    Sunflower("Sunflower"),
    Owl("Owl"),
    Teapot("Teapot"),
    Sailboat("Sailboat"),
    Apple("Apple"),
    Butterfly("Butterfly"),
    House("House"),
    Star("Star"),
    Fish("Fish"),
    Tree("Tree"),
    ;

    val id: String get() = name

    companion object {
        /** An unknown or missing id (for example after a downgrade) is no picture, never a crash. */
        fun fromId(id: String?): SafetyPicture? = entries.firstOrNull { it.id == id }
    }
}
