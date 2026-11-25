package com.example
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object CardDAO {
    fun createCard(): Int = transaction {
        CardTable.insertAndGetId {
            it[publicKey] = null
        }.value
    }

    fun updatePublicKey(id: Int, publicKey: String): Boolean = transaction {
        CardTable.update({ CardTable.id eq id }) {
            it[CardTable.publicKey] = publicKey
        } > 0
    }

    fun findById(id: Int): CardModel? = transaction {
        CardTable.select { CardTable.id eq id }
            .map { CardModel(it[CardTable.id].value, it[CardTable.publicKey]) }
            .singleOrNull()
    }
}