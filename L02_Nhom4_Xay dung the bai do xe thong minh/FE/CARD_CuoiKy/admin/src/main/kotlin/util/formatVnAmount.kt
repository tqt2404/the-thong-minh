package util

fun formatVnAmount(amountString: String): String {
    // Loại bỏ mọi ký tự không phải số
    val digits = amountString.filter { it.isDigit() }
    if (digits.isBlank()) return "0 đ"
    return digits.toLongOrNull()?.let {
        "%,d".format(it).replace(',', '.') + " đ"
    } ?: (amountString + " đ")
}
