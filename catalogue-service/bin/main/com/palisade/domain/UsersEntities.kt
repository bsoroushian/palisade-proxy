package com.palisade.catalogue.domain

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.date
import kotlinx.datetime.LocalDate

// 1. Database Table Schema (Automatically includes an 'id' auto-increment PK)
object Users : IntIdTable("users") {
    val name = varchar("name", 50)
    val email = varchar("email", 100).uniqueIndex()

    val phoneNumber = varchar("phone_number", 30).nullable()
    val birthDate = date("birth_date").nullable()
}

// 2. Exposed DAO Entity (Manages database operations and state)
class UserEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserEntity>(Users)

    var name by Users.name
    var email by Users.email

    var phoneNumber by Users.phoneNumber
    var birthDate by Users.birthDate

    // Optional: Converts the database entity into a clean domain model
    fun toDomain() = User(id.value, name, email, phoneNumber, birthDate)
}

// 3. Pure Domain Data Model (Used for Ktor serialization and business logic)
@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String,
    val phoneNumber: String?,
    val birthDate: LocalDate?,
)


@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val phoneNumber: String?,
    val birthDate: LocalDate?,
)
