package com.mosis.stepby.viewmodels

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.mosis.stepby.utils.ResponseStatus
import com.mosis.stepby.utils.FirestoreCollections
import com.mosis.stepby.utils.StorageFolders
import com.mosis.stepby.utils.UserInfoKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.*

class PersonalizeFragmentViewModel: ViewModel() {
    val responseStatus = MutableLiveData<ResponseStatus>()
    val instantToast = MutableLiveData<String>()


    suspend fun updateProfile(username: String?, fullName: String?, phone: String?, profilePicture: Bitmap?) {
        if (username.isNullOrBlank() || fullName.isNullOrBlank() || phone.isNullOrBlank()) {
            responseStatus.postValue(ResponseStatus(false, "Missing input."))
            return
        }

        withContext(Dispatchers.IO) {
            val email = Firebase.auth.currentUser!!.email!!
            val db = Firebase.firestore
            val storage = Firebase.storage
            var picName: String? = null

            // uploading profile picture
            if (profilePicture != null) {
                picName = UUID.randomUUID().toString() + ".jpg"
                val picRef = storage.reference.child(StorageFolders.IMAGES).child(picName!!)

                val baos = ByteArrayOutputStream()
                profilePicture.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                val data = baos.toByteArray()

                val uploadPicTask = picRef.putBytes(data)
                uploadPicTask.addOnFailureListener {
                    picName = null
                    instantToast.postValue("Couldn't upload image.")
                }

                uploadPicTask.await()
            }


            // add user info
            val userKey = db.collection(FirestoreCollections.USERS).document(email)
            val userInfo = hashMapOf(
                UserInfoKeys.USERNAME to username,
                UserInfoKeys.FULL_NAME to fullName,
                UserInfoKeys.PHONE to phone,
                UserInfoKeys.FRIENDS to listOf<String>(),
                UserInfoKeys.PROFILE_PICTURE to if (!picName.isNullOrBlank()) picName!! else DEFAULT_PROFILE_PICTURE
                )

            val usersRef  = db.collection(FirestoreCollections.USERNAMES).document(FirestoreCollections.USERNAMES)
            val t = db.runTransaction {
                val usersSnapshot = it.get(usersRef)

                if (usersSnapshot.exists()) {
                    val users = usersSnapshot.get(FirestoreCollections.USERNAMES) as List<String>
                    if (users.contains(username)) {
                        throw FirebaseFirestoreException("Username already in use", FirebaseFirestoreException.Code.ABORTED)
                    } else {
                        it.set(userKey, userInfo)
                        val updatedUsers = users + username
                        it.update(usersRef, FirestoreCollections.USERNAMES, updatedUsers)
                    }
                } else {
                    responseStatus.postValue(ResponseStatus(false, "Internal server error."))
                    Log.e(TAG, "usernames->usernames->usernames don't exist.")
                }
            }.addOnSuccessListener { result ->
                Log.d(TAG, "Transaction success: $result")
                responseStatus.postValue(ResponseStatus(true, ""))
            }.addOnFailureListener { e ->
                Log.d(TAG, "Transaction failure. ${e.message}")
                val msg: String = if (e.message != null) e.message!! else "Transaction failed"
                responseStatus.postValue(ResponseStatus(false, msg))
            }
        }
    }


    companion object {
        const val TAG = "PersonalizeFragmentViewModel"
        const val DEFAULT_PROFILE_PICTURE = "DEFAULT_PROFILE_PICTURE"
    }
}