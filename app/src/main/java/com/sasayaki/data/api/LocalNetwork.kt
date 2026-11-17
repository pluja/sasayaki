package com.sasayaki.data.api

import java.net.InetAddress

// .local, .home.arpa and .internal are reserved for private use; .lan and .localdomain are
// not, but routers hand them out for the home network.
private val localSuffixes = listOf(".local", ".lan", ".home.arpa", ".internal", ".localdomain")

/**
 * isLocalNetworkHost reports whether [host] names a machine on the person's own network or
 * tailnet: a private, loopback, link-local or CGNAT (Tailscale) address, a single-label name,
 * or a home-network suffix. It never resolves names; [LocalCleartextGuard] checks the address
 * actually reached. [host] is OkHttp-canonical, so IPv6 comes without brackets.
 */
fun isLocalNetworkHost(host: String): Boolean {
    val name = host.lowercase().trimEnd('.')
    if (':' in name) return isLocalNetworkAddress(parseIpv6(name))
    parseIpv4(name)?.let { return isLocalNetworkAddress(it) }
    if (name.all(Char::isDigit)) return false
    return name == "localhost" || '.' !in name || localSuffixes.any(name::endsWith)
}

fun isLocalNetworkAddress(address: InetAddress): Boolean {
    val bytes = address.address
    val isUniqueLocalIpv6 = bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    val isCarrierGradeNat = bytes.size == 4 && bytes[0].toInt() == 100 && (bytes[1].toInt() and 0xC0) == 0x40
    return address.isLoopbackAddress ||
        address.isSiteLocalAddress ||
        address.isLinkLocalAddress ||
        isUniqueLocalIpv6 ||
        isCarrierGradeNat
}

private fun parseIpv4(name: String): InetAddress? {
    val parts = name.split('.')
    if (parts.size != 4) return null
    val bytes = parts.map { part ->
        part.toIntOrNull()?.takeIf { part.isNotEmpty() && part.all(Char::isDigit) && it in 0..255 }
            ?: return null
    }
    return InetAddress.getByAddress(ByteArray(4) { bytes[it].toByte() })
}

// OkHttp only produces a host containing ':' for a valid IPv6 literal, and Java parses
// literals without a lookup.
private fun parseIpv6(name: String): InetAddress = InetAddress.getByName(name)
