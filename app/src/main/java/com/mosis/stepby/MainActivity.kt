package com.mosis.stepby

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.ActivityMainBinding
import com.mosis.stepby.viewmodels.MainActivityViewModel
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        //trySignOut()    // Starts app without logged in user

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        if (Firebase.auth.currentUser == null) {
            navController.navigate(R.id.action_welcomeFragment_to_signInFragment)
        } else {
            viewModel.importUserData()
            navController.navigate(R.id.action_welcomeFragment_to_homeFragment)
        }

        viewModel.locked.observe(this, Observer { locked ->
            if (locked){
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                binding.pbLoading.visibility = View.VISIBLE
            }
            else{
                binding.pbLoading.visibility = View.GONE
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        })

    }

    private fun trySignOut() {
        val auth = Firebase.auth
        if (auth.currentUser != null) {
            auth.signOut()
            Log.d(TAG, "User signed out.")
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}