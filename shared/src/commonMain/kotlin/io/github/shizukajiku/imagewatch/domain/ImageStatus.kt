package io.github.shizukajiku.imagewatch.domain

/** Situacion de una imagen vigilada tras el ultimo ciclo de verificacion. */
enum class ImageStatus {
    /** La version reconocida coincide con la que publica el origen, o es mas reciente. */
    OK,

    /** El origen publica una version mas reciente que la reconocida. */
    PENDING,

    /** Todavia no se ha verificado, o no hay version reconocida con la que comparar. */
    UNKNOWN,

    /** La ultima verificacion de esta imagen fallo. */
    ERROR,
}
