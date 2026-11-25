package com.example

import org.mindrot.jbcrypt.BCrypt

object PasswordUtil {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

    fun verify(password: String, hashed: String): Boolean = BCrypt.checkpw(password, hashed)


}



fun main() {
    val password = "123456"
    val hash = BCrypt.hashpw(password, BCrypt.gensalt())
    println(hash)
}