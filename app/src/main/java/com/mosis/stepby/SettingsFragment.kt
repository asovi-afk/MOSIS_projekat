package com.mosis.stepby

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.FragmentSettingsBinding
import com.mosis.stepby.services.GPSService
import com.mosis.stepby.viewmodels.MainActivityViewModel

class SettingsFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var binding: FragmentSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.tvSignOut.setOnClickListener {
            val intent = Intent(activity, GPSService::class.java)
            intent.putExtra(GPSService.CLEAR_USER, true) // value is not important
            activity?.startService(intent)
            mainVM.signOut.value = true
        }


        return binding.root
    }

    companion object {
        const val TAG = "SettingsFragment"
    }
}