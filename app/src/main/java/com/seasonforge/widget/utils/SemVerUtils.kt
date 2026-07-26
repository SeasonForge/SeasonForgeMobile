package com.seasonforge.widget.utils

object SemVerUtils {
    /**
     * Compares two version strings (e.g. "2.7" vs "v2.10.0").
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     */
    fun isNewer(currentVersion: String, remoteVersion: String): Boolean {
        val currentParts = parseVersion(currentVersion)
        val remoteParts = parseVersion(remoteVersion)

        val maxSize = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxSize) {
            val curr = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > curr) return true
            if (rem < curr) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> {
        val clean = version.trim().lowercase().removePrefix("v")
        val mainVersionPart = clean.split("-")[0]
        return mainVersionPart.split(".")
            .mapNotNull { part -> part.filter { char -> char.isDigit() }.toIntOrNull() }
    }
}
