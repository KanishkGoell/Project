package com.application.ocr.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    sealed class AuthResult {
        object Success : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            auth.signInWithEmailAndPassword(email, password).await()
            AuthResult.Success
        } catch (e: FirebaseAuthException) {
            AuthResult.Error(e.message ?: "Authentication failed")
        } catch (e: Exception) {
            AuthResult.Error("An unexpected error occurred")
        }
    }

    suspend fun register(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            auth.createUserWithEmailAndPassword(email, password).await()
            AuthResult.Success
        } catch (e: FirebaseAuthException) {
            AuthResult.Error(e.message ?: "Registration failed")
        } catch (e: Exception) {
            AuthResult.Error("An unexpected error occurred")
        }
    }

    fun logout() {
        auth.signOut()
    }
}