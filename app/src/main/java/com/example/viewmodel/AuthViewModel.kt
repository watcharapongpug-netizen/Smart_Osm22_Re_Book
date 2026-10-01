package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthManager
import com.example.data.auth.UserProfile
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for authentication workflows.
 */
sealed interface AuthUiState {
    object Idle : AuthUiState
    data class Loading(val message: String) : AuthUiState
    data class Success(val user: Any?, val message: String) : AuthUiState
    data class Error(val message: String, val throwable: Throwable? = null) : AuthUiState
}

/**
 * ViewModel managing authentication state and actions.
 */
class AuthViewModel(
    val authManager: AuthManager = AuthManager()
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authManager.currentUser
    val userProfile: StateFlow<UserProfile?> = authManager.userProfile
    val activeMembership: StateFlow<com.example.data.membership.UserMembership?> = authManager.activeMembership

    fun hasPermission(permission: com.example.data.membership.AreaPermission): Boolean {
        return authManager.activeMembership.value?.hasPermission(permission) ?: false
    }

    val currentUid: String?
        get() = authManager.currentUid

    val activeVillageId: String?
        get() = authManager.userProfile.value?.villageId

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun signInWithGoogle(context: Context, customClientId: String? = null) {
        _uiState.value = AuthUiState.Loading("กำลังเชื่อมต่อ Google Sign-In ผ่าน Credential Manager...")
        viewModelScope.launch {
            val result = authManager.signInWithGoogle(context, customClientId)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user, "เข้าสู่ระบบด้วย Google สำเร็จ")
                },
                onFailure = { error ->
                    val userFriendlyMsg = when {
                        error is androidx.credentials.exceptions.GetCredentialCancellationException ->
                            "ยกเลิกการเข้าสู่ระบบด้วย Google"
                        error.message?.contains("No credentials available", ignoreCase = true) == true ->
                            "ไม่พบบัญชี Google ที่พร้อมใช้งานบนอุปกรณ์นี้ (กรุณาลงชื่อเข้าใช้ Google ในการตั้งค่าโทรศัพท์)"
                        error.message?.contains("MISSING_WEB_CLIENT_ID") == true ->
                            "MISSING_WEB_CLIENT_ID"
                        error.message?.contains("Web Client ID", ignoreCase = true) == true ->
                            error.message ?: "กรุณาระบุ Web Client ID"
                        else ->
                            error.message ?: "เกิดข้อผิดพลาดในการเชื่อมต่อ Google Sign-In"
                    }
                    _uiState.value = AuthUiState.Error(userFriendlyMsg, error)
                }
            )
        }
    }

    fun signInWithEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน")
            return
        }
        _uiState.value = AuthUiState.Loading("กำลังเข้าสู่ระบบด้วยอีเมล...")
        viewModelScope.launch {
            val result = authManager.signInWithEmail(email, pass)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user, "เข้าสู่ระบบสำเร็จ")
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(
                        error.message ?: "เข้าสู่ระบบไม่สำเร็จ กรุณาตรวจสอบอีเมลหรือรหัสผ่าน",
                        error
                    )
                }
            )
        }
    }

    fun signUpWithEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthUiState.Error("รหัสผ่านต้องมีความยาวอย่างน้อย 6 ตัวอักษร")
            return
        }
        _uiState.value = AuthUiState.Loading("กำลังลงทะเบียนบัญชีผู้ใช้งานใหม่...")
        viewModelScope.launch {
            val result = authManager.signUpWithEmail(email, pass)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user, "ลงทะเบียนบัญชีใหม่สำเร็จ")
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(
                        error.message ?: "ลงทะเบียนไม่สำเร็จ กรุณาลองใหม่อีกครั้ง",
                        error
                    )
                }
            )
        }
    }

    fun signInAnonymously() {
        _uiState.value = AuthUiState.Loading("กำลังเข้าสู่ระบบชั่วคราว (Anonymous)...")
        viewModelScope.launch {
            val result = authManager.signInAnonymously()
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user, "เข้าสู่ระบบชั่วคราวสำเร็จ")
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(
                        error.message ?: "เข้าสู่ระบบชั่วคราวไม่สำเร็จ",
                        error
                    )
                }
            )
        }
    }

    fun signOut(context: Context? = null) {
        authManager.signOut(context)
        _uiState.value = AuthUiState.Idle
    }

    fun loadSurveyorProfile(context: Context) {
        authManager.loadSurveyorProfile(context)
    }

    fun saveSurveyorProfile(
        context: Context,
        fullName: String? = null,
        villageNo: String,
        villageName: String,
        subdistrict: String = "ต.ป่าขะ",
        district: String = "อ.บ้านนา",
        province: String = "จ.นครนายก",
        phone: String? = null,
        role: String? = "อสม. ประจำหมู่บ้าน",
        vhvCardId: String? = null,
        citizenId: String? = null,
        healthCenter: String? = null,
        photoUrl: String? = null,
        vhvCardPhotoUrl: String? = null
    ) {
        authManager.saveSurveyorProfile(
            context = context,
            fullName = fullName,
            villageNo = villageNo,
            villageName = villageName,
            subdistrict = subdistrict,
            district = district,
            province = province,
            phone = phone,
            role = role,
            vhvCardId = vhvCardId,
            citizenId = citizenId,
            healthCenter = healthCenter,
            photoUrl = photoUrl,
            vhvCardPhotoUrl = vhvCardPhotoUrl
        )
    }

    fun saveWebClientId(context: Context, clientId: String) {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("web_client_id", clientId.trim()).apply()
    }
}
