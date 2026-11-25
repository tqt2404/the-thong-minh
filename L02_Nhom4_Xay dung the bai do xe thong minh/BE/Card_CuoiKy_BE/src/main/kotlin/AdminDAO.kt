package com.example

import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.serialization.json.Json

object AdminDAO {
    fun insert(admin: Admin) = transaction {
        AdminTable.insert {
            it[id] = admin.id
            it[username] = admin.username
            it[passwordHash] = admin.passwordHash
            it[role] = admin.role
            it[permissions] = Json.encodeToString(admin.permissions)
            it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
        }
    }

    fun findByUsername(username: String): Admin? = transaction {
        AdminTable.select { AdminTable.username eq username }.singleOrNull()?.let {
            Admin(
                id = it[AdminTable.id],
                username = it[AdminTable.username],
                passwordHash = it[AdminTable.passwordHash],
                role = it[AdminTable.role],
                permissions = Json.decodeFromString(it[AdminTable.permissions]),
                createdAt = it[AdminTable.createdAt].toString()
            )
        }
    }

    // Thêm các hàm CRUD khác nếu muốn
}