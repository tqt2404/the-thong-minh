package com.example

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime // Bắt buộc phải import dòng này!

object AdminTable : Table("admins") {
    val id = varchar("id", 36)
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 16)
    val permissions = text("permissions")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}