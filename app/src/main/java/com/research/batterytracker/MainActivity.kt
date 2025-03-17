package com.research.batterytracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val RC_SIGN_IN = 9001
        private const val DEFAULT_SHEET_ID = "1Gw4zmwhbFNBTQDSjoYsyoGbIFP5LCzFL6MeT4Fyeuzk" // Google Sheet ID
    }
    
    private lateinit var signInButton: SignInButton
    private lateinit var signOutButton: Button
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    private lateinit var sheetIdEditText: EditText
    private lateinit var statusTextView: TextView
    
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var sheetsApiManager: SheetsApiManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        signInButton = findViewById(R.id.sign_in_button)
        signOutButton = findViewById(R.id.sign_out_button)
        startServiceButton = findViewById(R.id.start_service_button)
        stopServiceButton = findViewById(R.id.stop_service_button)
        sheetIdEditText = findViewById(R.id.sheet_id_edittext)
        statusTextView = findViewById(R.id.status_textview)
        
        // Initialize the SheetsApiManager
        sheetsApiManager = SheetsApiManager(this)
        
        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets"))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        // Set up click listeners
        signInButton.setOnClickListener { signIn() }
        signOutButton.setOnClickListener { signOut() }
        startServiceButton.setOnClickListener { startBatteryTrackingService() }
        stopServiceButton.setOnClickListener { stopBatteryTrackingService() }
        
        // Set default sheet ID if available
        val savedSheetId = DeviceUtils.getSheetId(this)
        if (savedSheetId != null) {
            sheetIdEditText.setText(savedSheetId)
        } else {
            sheetIdEditText.setText(DEFAULT_SHEET_ID)
        }
        
        // Update UI based on current state
        updateUI()
    }
    
    override fun onStart() {
        super.onStart()
        // Check if user is already signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        updateUI(account)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent()
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                // Signed in successfully
                Log.d(TAG, "signInResult:success")
                handleSignInSuccess(account)
            } catch (e: ApiException) {
                // Sign in failed
                Log.w(TAG, "signInResult:failed code=" + e.statusCode)
                updateUI(null)
                Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    
    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
        }
    }
    
    private fun handleSignInSuccess(account: GoogleSignInAccount?) {
        account?.apply {
            // Save the access token
            idToken?.let { token ->
                DeviceUtils.saveAccessToken(this@MainActivity, token)
                
                // Validate the sheet ID
                val sheetId = sheetIdEditText.text.toString()
                if (sheetId.isNotEmpty()) {
                    launch {
                        val isValid = sheetsApiManager.checkSheetAccess(sheetId, token)
                        if (isValid) {
                            DeviceUtils.saveSheetId(this@MainActivity, sheetId)
                            withContext(Dispatchers.Main) {
                                updateUI(account)
                                Toast.makeText(this@MainActivity, "Sheet ID validated successfully", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Invalid Sheet ID or insufficient permissions", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
        
        updateUI(account)
    }
    
    private fun updateUI(account: GoogleSignInAccount? = null) {
        if (account != null) {
            // User is signed in
            signInButton.visibility = View.GONE
            signOutButton.visibility = View.VISIBLE
            startServiceButton.isEnabled = true
            
            statusTextView.text = "Signed in as: ${account.email}"
        } else {
            // User is not signed in
            signInButton.visibility = View.VISIBLE
            signOutButton.visibility = View.GONE
            startServiceButton.isEnabled = false
            
            statusTextView.text = getString(R.string.sign_in_required)
        }
    }
    
    private fun startBatteryTrackingService() {
        // Save the current sheet ID
        val sheetId = sheetIdEditText.text.toString()
        if (sheetId.isNotEmpty()) {
            DeviceUtils.saveSheetId(this, sheetId)
        }
        
        // Start the foreground service
        val serviceIntent = Intent(this, BatteryTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        Toast.makeText(this, "Battery tracking service started", Toast.LENGTH_SHORT).show()
        startServiceButton.isEnabled = false
        stopServiceButton.isEnabled = true
    }
    
    private fun stopBatteryTrackingService() {
        val serviceIntent = Intent(this, BatteryTrackingService::class.java)
        stopService(serviceIntent)
        
        Toast.makeText(this, "Battery tracking service stopped", Toast.LENGTH_SHORT).show()
        startServiceButton.isEnabled = true
        stopServiceButton.isEnabled = false
    }
} 