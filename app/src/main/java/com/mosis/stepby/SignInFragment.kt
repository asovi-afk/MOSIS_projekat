package com.mosis.stepby

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.FragmentSignInBinding
import com.mosis.stepby.viewmodels.MainActivityViewModel
import com.mosis.stepby.viewmodels.SignInFragmentViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SignInFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var binding: FragmentSignInBinding
    private lateinit var viewModel: SignInFragmentViewModel
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel  =ViewModelProvider(this).get(SignInFragmentViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignInBinding.inflate(inflater, container, false)
        navController = findNavController()

        viewModel.responseStatus.observe(viewLifecycleOwner, Observer { response ->
            if (response.success) {
                navController.navigate(R.id.action_signInFragment_to_homeFragment)
            } else {
                Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
            }
            mainVM.locked.value = false
        })

        binding.btnLogin.setOnClickListener {
            val email = binding.editEmail.text.toString()
            val password = binding.editPassword.text.toString()

            mainVM.locked.value = true
            viewModel.login(email, password)
        }

        binding.tvRegister.setOnClickListener {
            navController.navigate(R.id.action_signInFragment_to_registerFragment)
        }

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModelStore.clear()
    }


    companion object {
        const val TAG = "SignInFragment"
    }
}