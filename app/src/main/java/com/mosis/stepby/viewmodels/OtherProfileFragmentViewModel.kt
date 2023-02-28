package com.mosis.stepby.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.auth.User
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.utils.FirestoreCollections
import com.mosis.stepby.utils.PendingFriendshipKeys
import com.mosis.stepby.utils.UserInfoKeys
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OtherProfileFragmentViewModel: ProfileFragmentViewModel() {

    fun update(email: String) {
        coroutineScope.launch(SupervisorJob()) {
            launch { updateUserBasicInfo(email) }
            launch { updateRunList(email) }
            launch { updateFriendList(email) }
        }
    }

    fun addFriend() {
        val myEmail = Firebase.auth.currentUser!!.email!!
        val otherEmail = userBasicInfo.value!!.email!!
        val otherAskedByRef = firestore.collection(FirestoreCollections.PENDING_FRIENDSHIPS).document(otherEmail)
        val meAskedByRef = firestore.collection(FirestoreCollections.PENDING_FRIENDSHIPS).document(myEmail)
        val otherRef = firestore.collection(FirestoreCollections.USERS).document(otherEmail)
        val meRef = firestore.collection(FirestoreCollections.USERS).document(myEmail)
        firestore.runTransaction {
            val otherAskedBy = it.get(otherAskedByRef).get(PendingFriendshipKeys.FROM) as MutableList<String>
            val meAskedBy = it.get(meAskedByRef).get(PendingFriendshipKeys.FROM) as MutableList<String>

            if (meAskedBy.contains(otherEmail)) {
                // Other user asked me and I'm accepting

                val otherFriends = it.get(otherRef).get(UserInfoKeys.FRIENDS) as MutableList<String>
                val myFriends = it.get(meRef).get(UserInfoKeys.FRIENDS) as MutableList<String>

                meAskedBy.remove(otherEmail)
                otherFriends.add(myEmail)
                myFriends.add(otherEmail)

                it.update(meAskedByRef, PendingFriendshipKeys.FROM, meAskedBy)
                it.update(otherRef, UserInfoKeys.FRIENDS, otherFriends)
                it.update(meRef, UserInfoKeys.FRIENDS, myFriends)
            } else if (!otherAskedBy.contains(myEmail)) {
                // Have to ask other user
                otherAskedBy.add(myEmail)
                it.update(otherAskedByRef, PendingFriendshipKeys.FROM, otherAskedBy)
            } else {
                // Already asked or are already friends. Do nothing
            }
        }
            .addOnSuccessListener { _instantToast.value = "Request sent." }
            .addOnFailureListener { _instantToast.value = "Could not send friend request." }
    }
}