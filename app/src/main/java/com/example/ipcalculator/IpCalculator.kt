package com.example.ipcalculator


import java.io.OutputStream

/**
 * Доменная логика IP-калькулятора.
 * Без Android-зависимостей: можно покрыть юнит-тестами.
 */
object IpCalculator {

    data class SubnetResult(
        val networkAddress: String,
        val cidr: Int,
        val subnetMask: String,
        val wildcardMask: String,
        val broadcastAddress: String,
        val firstUsableIp: String,
        val lastUsableIp: String,
        val totalAddresses: Long,
        val usableHosts: Long
    ) {
        fun toText(): String = buildString {
            appendLine("Сеть: $networkAddress/$cidr")
            appendLine("Маска: $subnetMask")
            appendLine("Wildcard: $wildcardMask")
            appendLine("Broadcast: $broadcastAddress")
            appendLine("Первый хост: $firstUsableIp")
            appendLine("Последний хост: $lastUsableIp")
            appendLine("Всего адресов: $totalAddresses")
            append("Доступно хостов: $usableHosts")
        }
    }

    data class SplitResult(val totalCount: Long, val subnets: List<SubnetResult>)

    // ---------- Преобразования ----------

    fun ipToLong(ip: String): Long {
        val parts = ip.trim().split(".")
        if (parts.size != 4) {
            throw IllegalArgumentException("IPv4 должен содержать 4 октета, получено ${parts.size}")
        }
        var result = 0L
        for (part in parts) {
            val octet = part.toIntOrNull()
                ?: throw IllegalArgumentException("Октет не является числом: \"$part\"")
            if (octet !in 0..255) {
                throw IllegalArgumentException("Октет должен быть 0..255: \"$part\"")
            }
            result = (result shl 8) or octet.toLong()
        }
        return result
    }

    fun longToIp(value: Long): String = buildString {
        append((value shr 24) and 0xFF); append('.')
        append((value shr 16) and 0xFF); append('.')
        append((value shr 8) and 0xFF); append('.')
        append(value and 0xFF)
    }

    fun prefixToMask(prefix: Int): Long {
        checkPrefix(prefix)
        return if (prefix == 0) 0L else ((0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL)
    }

    fun prefixToWildcard(prefix: Int): Long = prefixToMask(prefix).inv() and 0xFFFFFFFFL

    /** "255.255.255.0" -> 24. Проверяет непрерывность маски. */
    fun maskToPrefix(mask: String): Int {
        val maskLong = ipToLong(mask)
        var prefix = 0
        var seenZero = false
        for (bit in 31 downTo 0) {
            val isOne = ((maskLong shr bit) and 1L) == 1L
            if (isOne && seenZero) {
                throw IllegalArgumentException("Некорректная маска $mask: единицы должны идти непрерывно")
            }
            if (isOne) prefix++ else seenZero = true
        }
        return prefix
    }

    /** Принимает "24", "/24" или "255.255.255.0". */
    fun resolvePrefix(maskOrCidr: String): Int {
        val s = maskOrCidr.trim()
        if (s.isEmpty()) throw IllegalArgumentException("Укажи маску или префикс")
        return if (s.contains('.')) maskToPrefix(s) else parsePrefix(s)
    }

    fun parsePrefix(s: String): Int {
        val clean = s.trim().removePrefix("/")
        val value = clean.toIntOrNull()
            ?: throw IllegalArgumentException("Префикс не является числом: \"$s\"")
        checkPrefix(value)
        return value
    }

    private fun checkPrefix(prefix: Int) {
        if (prefix !in 0..32) {
            throw IllegalArgumentException("Префикс должен быть 0..32, получено $prefix")
        }
    }

    // ---------- Основной расчёт ----------

    fun calculate(ip: String, maskOrCidr: String): SubnetResult =
        calculate(ip, resolvePrefix(maskOrCidr))

    fun calculate(ip: String, prefix: Int): SubnetResult {
        checkPrefix(prefix)
        val address = ipToLong(ip)
        val mask = prefixToMask(prefix)
        val wildcard = mask.inv() and 0xFFFFFFFFL

        val network = address and mask
        val broadcast = network or wildcard
        val total = 1L shl (32 - prefix)

        val first: Long
        val last: Long
        val usable: Long
        when {
            prefix == 32 -> { first = network; last = network; usable = 1 }
            prefix == 31 -> { first = network; last = broadcast; usable = 2 }
            else -> { first = network + 1; last = broadcast - 1; usable = total - 2 }
        }

        return SubnetResult(
            networkAddress = longToIp(network),
            cidr = prefix,
            subnetMask = longToIp(mask),
            wildcardMask = longToIp(wildcard),
            broadcastAddress = longToIp(broadcast),
            firstUsableIp = longToIp(first),
            lastUsableIp = longToIp(last),
            totalAddresses = total,
            usableHosts = usable
        )
    }

    // ---------- Разбиение на подсети (пригодится в Спринте 2) ----------

    fun splitNetwork(
        networkIp: String,
        currentPrefix: Int,
        newPrefix: Int,
        limit: Int = 500
    ): SplitResult {
        if (newPrefix < currentPrefix) {
            throw IllegalArgumentException("Новый префикс должен быть не меньше текущего ($currentPrefix)")
        }
        checkPrefix(newPrefix)
        val base = ipToLong(networkIp) and prefixToMask(currentPrefix)
        val totalCount = 1L shl (newPrefix - currentPrefix)
        val size = 1L shl (32 - newPrefix)
        val shown = minOf(totalCount, limit.toLong())
        val subnets = List(shown.toInt()) { i ->
            calculate(longToIp(base + i * size), newPrefix)
        }
        return SplitResult(totalCount, subnets)
    }

    fun exportSplitToText(original: SubnetResult, split: SplitResult): String {
        val newPrefix = split.subnets.firstOrNull()?.cidr ?: original.cidr

        return buildString {
            appendLine("Разбиение сети ${original.networkAddress}/${original.cidr} на подсети /$newPrefix")
            appendLine("Всего подсетей: ${split.totalCount}, в файле: ${split.subnets.size}")
            appendLine("========================================")
            appendLine()

            split.subnets.forEachIndexed { index, subnet ->
                appendLine("Подсеть №${index + 1}: ${subnet.networkAddress}/${subnet.cidr}")
                appendLine("  Маска: ${subnet.subnetMask}")
                appendLine("  Broadcast: ${subnet.broadcastAddress}")
                appendLine("  Первый хост: ${subnet.firstUsableIp}")
                appendLine("  Последний хост: ${subnet.lastUsableIp}")
                appendLine("  Хостов: ${subnet.usableHosts}")
                appendLine()
            }
        }
    }

    /**
     * Записывает ВСЕ подсети в поток построчно, не создавая список в памяти.
     * Это решает проблему больших сетей (например, /8 → /24 = 65536 подсетей).
     */
    fun writeSplitToStream(
        outputStream: java.io.OutputStream,
        original: SubnetResult,
        newPrefix: Int
    ) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            val totalSubnets = 1L shl (newPrefix - original.cidr)
            writer.write("Разбиение сети ${original.networkAddress}/${original.cidr} на подсети /$newPrefix\n")
            writer.write("Всего подсетей: $totalSubnets\n")
            writer.write("========================================\n\n")

            val base = ipToLong(original.networkAddress)
            val subnetSize = 1L shl (32 - newPrefix)

            for (i in 0 until totalSubnets) {
                val subnetAddress = longToIp(base + i * subnetSize)
                val subnet = calculate(subnetAddress, newPrefix)

                writer.write("Подсеть №${i + 1}: ${subnet.networkAddress}/${subnet.cidr}\n")
                writer.write("  Маска: ${subnet.subnetMask}\n")
                writer.write("  Broadcast: ${subnet.broadcastAddress}\n")
                writer.write("  Первый хост: ${subnet.firstUsableIp}\n")
                writer.write("  Последний хост: ${subnet.lastUsableIp}\n")
                writer.write("  Хостов: ${subnet.usableHosts}\n\n")
            }
        }
    }
}