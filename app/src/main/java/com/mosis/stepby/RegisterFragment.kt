package com.mosis.stepby

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.mosis.stepby.databinding.FragmentRegisterBinding
import com.mosis.stepby.viewmodels.MainActivityViewModel
import com.mosis.stepby.viewmodels.RegisterFragmentViewModel

class RegisterFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var binding: FragmentRegisterBinding
    private lateinit var viewModel: RegisterFragmentViewModel
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(RegisterFragmentViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        navController = findNavController()
        binding = FragmentRegisterBinding.inflate(inflater, container, false)

        viewModel.responseStatus.observe(viewLifecycleOwner, Observer { response ->
            if (response.success) {
                navController.navigate(R.id.action_registerFragment_to_personalizeFragment)
            } else {
                Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
            }

            mainVM.locked.value = false
        })

        binding.btnRegister.setOnClickListener {
            mainVM.locked.value = true

            val email = binding.editEmail.text.toString()
            val password = binding.editPassword.text.toString()
            val confPassword = binding.editConfirmPassword.text.toString()


            viewModel.register(email, password, confPassword)
        }

        binding.tvLogin.setOnClickListener {
            navController.navigate(R.id.action_registerFragment_to_signInFragment)
        }

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModelStore.clear()
    }

    companion object {
        const val T= "RegisterFragment"
    }
}