package app.boomdoom.core.model

/** Preset learning topics (Product Spec §7.4 item 1). `id` keys the bundled artwork and starter lesson. */
enum class PresetTopic(val id: String, val displayName: String, val shortName: String, val backendName: String) {
    PSYCHOLOGY("psychology", "Psychology", "Psychology", "Psychology"),
    INTERVIEW("interview", "Interview Prep", "Interview Prep", "Interview preparation"),
    GK("gk", "General Knowledge", "GK", "General knowledge"),
    FINANCE("finance", "Personal Finance", "Finance", "Personal finance basics"),
    SCIENCE("science", "Science", "Science", "Science facts"),
    HISTORY("history", "History", "History", "History"),
    COMMUNICATION("communication", "Communication", "Communication", "Communication skills"),
    TECH("tech", "Tech Explained", "Tech", "Tech explained"),
    ;

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
        fun byBackendName(name: String) = entries.firstOrNull { it.backendName.equals(name, ignoreCase = true) }
    }
}

enum class Level(val api: String, val label: String) {
    BEGINNER("beginner", "Beginner"), INTERMEDIATE("intermediate", "Intermediate"), ADVANCED("advanced", "Advanced");

    companion object {
        fun of(api: String) = entries.firstOrNull { it.api == api } ?: BEGINNER
    }
}
