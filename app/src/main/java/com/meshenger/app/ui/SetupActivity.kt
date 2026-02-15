package com.daricheh.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.daricheh.app.MeshApplication
import com.daricheh.app.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {

    private var _binding: ActivitySetupBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { MeshApplication.instance }

    private val phoneHintLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK && result.data != null) {
                val phone = Identity.getSignInClient(this).getPhoneNumberFromIntent(result.data)
                binding.etPhone.setText(phone)
                binding.tvPhoneVerified.visibility = View.VISIBLE
                binding.btnSharePhone.text = "✓ شماره تأیید شد"
                binding.btnSharePhone.isEnabled = false
                app.log("Setup: Phone received: $phone")
            }
        } catch (e: Exception) {
            app.log("Setup: Phone hint error: ${e.message}")
            Toast.makeText(this, "لطفاً شماره را دستی وارد کنید", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (app.isSetupComplete) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        try {
            _binding = ActivitySetupBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading setup: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.btnSharePhone.setOnClickListener {
            try {
                val req = GetPhoneNumberHintIntentRequest.builder().build()
                Identity.getSignInClient(this).getPhoneNumberHintIntent(req)
                    .addOnSuccessListener { pi ->
                        try {
                            phoneHintLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } catch (e: Exception) {
                            app.log("Setup: Launch error: ${e.message}")
                        }
                    }
                    .addOnFailureListener { e ->
                        app.log("Setup: Phone hint failed: ${e.message}")
                        Toast.makeText(this, "لطفاً شماره را دستی وارد کنید", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                app.log("Setup: Phone hint error: ${e.message}")
                Toast.makeText(this, "لطفاً شماره را دستی وارد کنید", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStart.setOnClickListener {
            try {
                val name = binding.etUsername.text?.toString()?.trim() ?: ""
                val phone = binding.etPhone.text?.toString()?.trim() ?: ""

                if (name.length < 2) {
                    Toast.makeText(this, "نام باید حداقل ۲ حرف باشد", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (phone.length < 10) {
                    Toast.makeText(this, "شماره موبایل معتبر وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                app.username = name
                app.phoneNumber = phone
                app.log("Setup: Completed. Name=$name Phone=$phone")

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                app.log("Setup: Start error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}