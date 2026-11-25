package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    KeyUtil.generateKeyPairIfNotExists()
    DatabaseFactory.init(environment.config)
    transaction {
        SchemaUtils.create(
            AdminTable,
            CardTable
        )
    }
    configureFrameworks()
    configureHTTP()
    configureSerialization()
    configureJwtAuth()
    configureSecurity()
    configureMonitoring()
    configureRouting()

}
