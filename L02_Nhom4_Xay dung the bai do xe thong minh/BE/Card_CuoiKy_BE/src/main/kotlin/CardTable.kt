package com.example

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.dao.id.IntIdTable

object CardTable : IntIdTable("cards") {
    val publicKey = text("public_key").nullable()
}