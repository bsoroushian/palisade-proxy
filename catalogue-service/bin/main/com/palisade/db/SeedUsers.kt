package com.palisade.catalogue.db

import com.palisade.catalogue.domain.Users
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import io.ktor.server.application.Application
import kotlinx.datetime.LocalDate

fun Application.seedDatabase() {
    transaction {
        SchemaUtils.create(Users)

            // optional: avoid re-seeding duplicates
        val existing = Users.selectAll().limit(1).any()
        if (!existing) {
            val aliceId = Users.insertAndGetId {
                it[name] = "Alice"
                it[email] = "alice@example.com"
                it[phoneNumber] = "+1-212-555-1256"
                it[birthDate] = LocalDate(1983,1,23)
            }
            
            val bobId = Users.insertAndGetId {
                it[name] = "Bob"
                it[email] = "bob@example.com"
                it[phoneNumber] = "+1-415-555-0134"
                it[birthDate] = LocalDate(1989,6,14)
            }
        }
    }
}