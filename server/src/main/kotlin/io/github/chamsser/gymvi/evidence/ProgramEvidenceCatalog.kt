package io.github.chamsser.gymvi.evidence

import io.github.chamsser.gymvi.catalog.GymviApiException

interface ProgramEvidenceCatalog {
    fun find(usageOptionId: String): ProgramEvidenceCatalogRecord?
}

class UnavailableProgramEvidenceCatalog : ProgramEvidenceCatalog {
    override fun find(usageOptionId: String): ProgramEvidenceCatalogRecord? =
        throw GymviApiException(
            "DATASET_UNAVAILABLE",
            "활성 프로그램 데이터셋을 조회할 수 없습니다.",
            true,
        )
}
