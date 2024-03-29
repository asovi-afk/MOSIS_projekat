package com.mosis.stepby

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.FragmentWelcomeBinding
import com.mosis.stepby.viewmodels.MainActivityViewModel

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

class WelcomeFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var binding: FragmentWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false)

        mainVM.showBNV.value = false

        val navController = findNavController()
        if (Firebase.auth.currentUser == null) {
            navController.navigate(R.id.action_welcomeFragment_to_signInFragment)
        } else {
            mainVM.importUserData()
            navController.navigate(R.id.action_welcomeFragment_to_homeFragment)
        }

        return binding.root
    }

    companion object {
        const val TAG = "WelcomeFragment"
    }
}