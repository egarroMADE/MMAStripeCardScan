package com.mademediacorp.mmastripecardscan.payment.card

import android.util.Log

/**
 * A class that provides a method to determine if a DNI is valid.
 */
interface DNIValidator {
    fun isValidDNI(dni: String): Boolean

    operator fun plus(other: DNIValidator): DNIValidator = CompositeDNIValidator(this, other)
}

/**
 * A [PanValidator] comprised of two separate validators.
 */
private class CompositeDNIValidator(
    private val validator1: DNIValidator,
    private val validator2: DNIValidator
) : DNIValidator {
    override fun isValidDNI(dni: String): Boolean =
        validator1.isValidDNI(dni) && validator2.isValidDNI(dni)
}

/**
 * A [PanValidator] that ensures the DNI is of a valid length.
 */
object LengthDNIValidator : DNIValidator {
    override fun isValidDNI(dni: String): Boolean {
        Log.d("DNI",dni)
        return dni.length == 90
    }
}

/**
 * A [DNIValidator] that performs check digits for validation.
 *
 */
object MRZDNIValidator : DNIValidator {
    override fun isValidDNI(dni: String): Boolean {
        if (dni.isEmpty()) {
            return false
        }
        if (!dni.contains("<")) return false
        val line1 = dni.take(30)
        val line2 = dni.drop(30).take(30)

        // Extract fields as per MRZ spec
        val nationality = line1.drop(2).take(3)
        val serialNumber = line1.drop(5).take(9)
        val field3CheckDigit = line1.drop(14).take(1)
        val dniNumber = line1.drop(15).take(9)
        val stuffing1 = line1.drop(24).take(6)

        val dob = line2.take(6)
        val dobCheckDigit = line2.drop(6).take(1)
        val sex = line2.drop(7).take(1)
        val expiry = line2.drop(8).take(6)
        val expiryCheckDigit = line2.drop(14).take(1)
        val stuffing2 = line2.drop(18).take(11)
        val overallCheckDigit = line2.drop(29).take(1)

        // Validate stuffing
        if (stuffing1.any { it != '<' } || stuffing2.any { it != '<' }) return false
//        Log.d("DNI","Stuffings passed!")
        // Validate nationality
        if (nationality != "ESP") return false
//        Log.d("DNI","Nationality passed!")
        // Validate check digits
        if (!isValidCheckDigit(serialNumber, field3CheckDigit) ||
            !isValidCheckDigit(dob, dobCheckDigit) ||
            !isValidCheckDigit(expiry, expiryCheckDigit) ||
            !isValidCheckDigit(
                serialNumber + field3CheckDigit + dniNumber + dob + dobCheckDigit + expiry + expiryCheckDigit,
                overallCheckDigit
            )
        ) return false
//        Log.d("DNI","Check-digits passed!")
        // Validate gender
        if (sex != "M" && sex != "F" && sex != "<") return false
//        Log.d("DNI","Gender passed!")
        // DNI is valid
//        Log.d("DNI","DNI passed!!")
        return true
    }

    private fun isValidCheckDigit(data: String, expected: String): Boolean {
        val expectedDigit = expected.toIntOrNull() ?: return false
        return computeCheckDigit(data) == expectedDigit
    }

    // ICAO 9303 check digit computation
    private fun computeCheckDigit(input: String): Int {
        val weights = listOf(7, 3, 1)
        var sum = 0

        for (i in input.indices) {
            val char = input[i]
            val value = when {
                char in '0'..'9' -> char.toString().toInt()
                char in 'A'..'Z' -> char.code - 'A'.code + 10
                char == '<' -> 0
                else -> return -1 // Invalid character
            }
            sum += value * weights[i % 3]
        }
        return sum % 10
    }
}
