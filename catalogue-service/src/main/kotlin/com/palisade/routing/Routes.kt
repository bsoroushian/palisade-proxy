package com.palisade.catalogue.routing

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.request.receive
import io.ktor.server.routing.routing
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.principal
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import com.palisade.catalogue.domain.UserEntity
import com.palisade.catalogue.domain.Users
import com.palisade.catalogue.domain.CreateUserRequest

fun Application.configureRouting() {
    routing {
        route("/v1") {
            // GET /v1/user
            get("/user") {
                val users = transaction {
                    UserEntity.all()
                        .map { it.toDomain() }
                }

                call.respond(users)
            }

            // GET /v1/user/{id}
            get("/user/{id}") {
                val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
                val id = idParam.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "id must be an integer")

                val user = transaction {
                    UserEntity.find { Users.id eq id }.singleOrNull()?.toDomain()
                }

                user?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound, "User not found")            
            }

            authenticate("basic-auth"){
                // POST /v1/user
                post("/user") {
                    val body = call.receive<CreateUserRequest>()

                    val createdOrExisting = transaction {
                        // If email already exists, return it; otherwise create
                        UserEntity.find { Users.email eq body.email }.singleOrNull()?.toDomain()
                            ?: UserEntity.new {
                                name = body.name
                                email = body.email
                                phoneNumber = body.phoneNumber
                                birthDate = body.birthDate
                            }.toDomain()
                    }

                    call.respond(HttpStatusCode.Created, createdOrExisting)            
                }

                // PUT /v1/user/{id}
                put("/user/{id}") {
                    val principal = call.principal<UserIdPrincipal>()

                    val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
                    val id = idParam.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest, "id must be an integer")

                    // 1. Fetch current database record
                    val existingUser = transaction {
                        UserEntity.findById(id)?.toDomain()
                    }

                    if (existingUser == null) {
                        return@put call.respond(HttpStatusCode.NotFound, "User not found")
                    }

                    // 2. Authorization Check (Principal matching)
                    if (principal?.name != existingUser.name) {
                        return@put call.respond(HttpStatusCode.Forbidden, "You can only update your own profile")
                    }

                    // 3. Receive the complete PUT body
                    val payload = try {
                        call.receive<CreateUserRequest>()
                    } catch (e: Exception) {
                        return@put call.respond(HttpStatusCode.BadRequest, "Invalid request body format: ${e.localizedMessage}")
                    }

                    // 4. Validate Immutability (Combined secure check)
                    if (payload.name != existingUser.name || payload.email != existingUser.email) {
                        return@put call.respond(HttpStatusCode.BadRequest, "Invalid request payload profile data")
                    }

                    // 5. Overwrite mutable fields
                    val updatedUser = transaction {
                        val entity = UserEntity.findById(id)
                        entity?.apply {
                            this.phoneNumber = payload.phoneNumber
                            this.birthDate = payload.birthDate
                        }
                        entity?.toDomain()
                    }

                    if (updatedUser == null) {
                        return@put call.respond(HttpStatusCode.InternalServerError, "Failed to update record state")
                    }

                    call.respond(HttpStatusCode.OK, updatedUser)
                }

                // DELETE /v1/user/{id}
                delete("/user/{id}") {
                    val principal = call.principal<UserIdPrincipal>()

                    val idParam = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
                    val id = idParam.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, "id must be an integer")

                    val existingUser = transaction {
                        UserEntity.findById(id)?.toDomain()
                    }

                    if (existingUser == null) {
                        return@delete call.respond(HttpStatusCode.NotFound, "User not found")
                    }

                    println("DEBUG AUTH: Principal Name = '${principal?.name}', DB Name = '${existingUser.name}'")
                    if (principal?.name != existingUser.name) {
                        return@delete call.respond(HttpStatusCode.Forbidden, "You can only delete your own profile")
                    }

                    transaction {
                        UserEntity.findById(id)?.delete()
                    }

                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        // Serve the static OpenAPI specification file at the root
        get("/openapi") {
            call.respond(HttpStatusCode.OK, this::class.java.classLoader.getResourceAsStream("openapi.json")?.readBytes() ?: byteArrayOf())
        }
    }
}
