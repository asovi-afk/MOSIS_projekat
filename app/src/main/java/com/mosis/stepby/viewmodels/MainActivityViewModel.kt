package com.mosis.stepby.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivityViewModel : ViewModel() {
    val locked = MutableLiveData<Boolean>()
    val showBNV = MutableLiveData<Boolean>(false)
    val signOut = MutableLiveData<Boolean>()
    val runBackground = MutableLiveData<Boolean>(true)
    val loggedIn = MutableLiveData<Boolean>(false)

    var userPreviewEmail: String? = null

    private lateinit var currentUserKey: String

    public fun importUserData() {
        //locked.value = true
        // TODO : Implement this shit
    }

    public fun getCurrentUserKey(): String? {
        return if (!::currentUserKey.isInitialized)  currentUserKey else  null
    }
}