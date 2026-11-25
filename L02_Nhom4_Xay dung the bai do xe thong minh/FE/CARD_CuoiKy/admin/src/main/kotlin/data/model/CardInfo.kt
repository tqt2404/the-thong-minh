package data.model

data class CardInfo(
    val idCard: String,
    val fullName: String,
    val dateOfBirth: String,
    val address: String,
    val phoneNumber: String,
    //val expired: String,
    val pin: String,
    val avatarBytes: ByteArray   // bytes của ảnh đã chuẩn hoá/nén, max 8192
)