package com.mademediacorp.mmastripecardscan.payment.card

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Enhanced ScannedCard data class supporting both PAN and DNI
 */
@Parcelize
data class ScannedCard(
    val value: String? = null,
    val recognitionMethod: String? = null
) : Parcelable {

    /**
     * Check if any valid document was found
     */
    val hasValidDocument: Boolean
        get() = value != null

    /**
     * Get the primary document type found
     */
    val documentType: DocumentType
        get() {
            return when (recognitionMethod) {
                "TENSORFLOW" -> DocumentType.CREDIT_CARD
                "ML_KIT" -> DocumentType.DNI
                else -> DocumentType.UNKNOWN
            }
        }

    enum class DocumentType {
        CREDIT_CARD,
        DNI,
        UNKNOWN
    }

    /**
     * Force a generic toString method to prevent leaking information about this class'
     * parameters after R8. Without this method, this `data class` will automatically generate a
     * toString which retains the original names of the parameters even after obfuscation.
     */
    override fun toString(): String {
        return "ScannedCard"
    }
}