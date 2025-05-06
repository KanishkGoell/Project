package com.application.ocr

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.application.ocr.data.AuthRepository
import com.application.ocr.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if user is already logged in
        if (authRepository.isUserLoggedIn) {
            navigateToHome()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                loginUser()
            }
        }

        binding.btnRegister.setOnClickListener {
            if (validateInputs()) {
                registerUser()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.email_required)
            return false
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.password_required)
            return false
        }

        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.password_length)
            return false
        }

        // Clear errors
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        return true
    }

    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        showLoading(true)

        lifecycleScope.launch {
            when (val result = authRepository.login(email, password)) {
                is AuthRepository.AuthResult.Success -> {
                    showLoading(false)
                    navigateToHome()
                }
                is AuthRepository.AuthResult.Error -> {
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        showLoading(true)

        lifecycleScope.launch {
            when (val result = authRepository.register(email, password)) {
                is AuthRepository.AuthResult.Success -> {
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, R.string.registration_success, Toast.LENGTH_SHORT).show()
                    navigateToHome()
                }
                is AuthRepository.AuthResult.Error -> {
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnRegister.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}