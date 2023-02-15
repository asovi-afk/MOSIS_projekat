package com.mosis.stepby.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.utils.ResponseStatus
import kotlinx.coroutines.*

class SignInFragmentViewModel: ViewModel() {
    val responseStatus = MutableLiveData<ResponseStatus>()

    fun login(email: String?, password: String?) {
        if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
            val auth = Firebase.auth
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d(TAG, "Logged in")
                        responseStatus.postValue(ResponseStatus(true, ""))
                    } else {
                        Log.d(TAG, "Failed Login: " + it.exception?.message )
                        responseStatus.postValue(ResponseStatus(false, "Failed login: "+ it.exception?.message))
                    }
                }
        } else {
            Log.d(TAG, "Both fields required.")
            responseStatus.value = ResponseStatus(false, "Both fields required.")
        }
    }
    suspend fun test() {
        coroutineScope {
            Thread.sleep(8000)
            Log.d(PersonalizeFragmentViewModel.TAG, "After Sleep #: ${Thread.currentThread().name}")
            Log.d(PersonalizeFragmentViewModel.TAG, "After RunBlocking #: ${Thread.currentThread().name}")
        }
    }
    companion object {
        const val TAG = "SignInFragmentViewModel"
    }
}