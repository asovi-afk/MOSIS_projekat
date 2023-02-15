package com.mosis.stepby.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.utils.ResponseStatus

class RegisterFragmentViewModel: ViewModel() {
    val responseStatus = MutableLiveData<ResponseStatus>()

    fun register(email: String?, password: String?, confPassword: String?) {
        if (email.isNullOrBlank() || password.isNullOrBlank() || confPassword.isNullOrBlank()) {
            responseStatus.value = ResponseStatus(false, "Missing input.")
        } else if(password != confPassword) {
            responseStatus.value = ResponseStatus(false, "Passwords doesn't match.")
        } else {
            val auth = Firebase.auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener{ task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "createUserWithEmail:success")
                        responseStatus.postValue(ResponseStatus(true, ""))
                    } else {
                        Log.d(TAG, "createUserWithEmail: " + task.exception?.message)
                        responseStatus.postValue(ResponseStatus(false, "Failed registration: " + task.exception?.message))
                    }
                }
        }
    }


    companion object {
        const val TAG = "RegisterFragmentViewModel"
    }

}