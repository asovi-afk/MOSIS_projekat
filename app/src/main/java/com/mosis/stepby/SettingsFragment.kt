package com.mosis.stepby

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.FragmentSettingsBinding
import com.mosis.stepby.services.GPSService
import com.mosis.stepby.viewmodels.MainActivityViewModel

class SettingsFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var binding: FragmentSettingsBinding

    private lateinit var runInBackgroundObserver: Observer<Boolean>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        runInBackgroundObserver = Observer { binding.swcRunInBackground.isChecked = it}

        binding.tvSignOut.setOnClickListener { mainVM.signOut.value = true }
        binding.swcRunInBackground.setOnClickListener { mainVM.runBackground.value = !mainVM.runBackground.value!! }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        mainVM.runBackground.observe(this, runInBackgroundObserver)
    }

    override fun onPause() {
        mainVM.runBackground.removeObserver(runInBackgroundObserver)
        super.onPause()
    }

    companion object {
        const val TAG = "SettingsFragment"
    }
}