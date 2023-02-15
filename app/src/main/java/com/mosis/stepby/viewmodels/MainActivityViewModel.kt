package com.mosis.stepby.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivityViewModel : ViewModel() {
    val locked = MutableLiveData<Boolean>()

    private lateinit var currentUserKey: String

    public fun importUserData() {
        //locked.value = true
        // TODO : Implement this shit
    }

    public fun getCurrentUserKey(): String? {
        return if (!::currentUserKey.isInitialized)  currentUserKey else  null
    }
}