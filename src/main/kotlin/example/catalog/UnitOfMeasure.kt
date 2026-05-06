package example.catalog

import com.fasterxml.jackson.annotation.JsonProperty

enum class UnitOfMeasure {
    @JsonProperty("Millimeter")
    MILLIMETER,

    @JsonProperty("Centimeter")
    CENTIMETER,
}
