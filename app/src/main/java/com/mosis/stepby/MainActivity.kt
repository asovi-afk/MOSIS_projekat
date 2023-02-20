package com.mosis.stepby

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.forEach
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.ActivityMainBinding
import com.mosis.stepby.services.GPSService
import com.mosis.stepby.viewmodels.MainActivityViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.GlobalScope.coroutineContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainActivityViewModel by viewModels()

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setBottomNavigationView()

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

        viewModel.showBNV.observe(this, Observer { show -> binding.bottomNavigationView.visibility = if (show) View.VISIBLE else View.GONE})

        viewModel.signOut.observe(this, Observer {
            stopServices()
            Firebase.auth.signOut()
            binding.bottomNavigationView.menu.findItem(R.id.home)?.isChecked = true
            navController.navigate(R.id.action_settingsFragment_to_welcomeFragment)
        })

        viewModel.loggedIn.observe(this, Observer { if (it) startServicesInBackground()})
    }

    override fun onBackPressed() {
        binding.bottomNavigationView.menu.findItem(R.id.home)?.isChecked = true
        super.onBackPressed()
    }

    override fun onPause() {
        if (!viewModel.runBackground.value!!) stopServices() else startServicesInForeground()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.loggedIn.value!!) startServicesInBackground()
    }

    private fun stopServices() {
        val intent = Intent(this, GPSService::class.java)
        stopService(intent)
    }

    private fun startServicesInBackground() {
        Intent(this, GPSService::class.java).also { intent ->
            intent.putExtra(GPSService.STOP_FOREGROUND, true)
            startService(intent) // so service won't get destroyed when nothing binds to it
        }
    }

    private fun startServicesInForeground() {
        Intent(this, GPSService::class.java).also { intent ->
            intent.putExtra(GPSService.START_FOREGROUND, true)
            startService(intent)
        }
    }

    private fun setBottomNavigationView() {
        binding.bottomNavigationView.menu.forEach {
            when (it.itemId) {
                R.id.home -> defineNavBarHome(it)
                R.id.settings -> defineNavBarSettings(it)
                R.id.profile -> defineNavBarProfile(it)
            }
        }
    }

    private fun defineNavBarHome(item: MenuItem) {
        item.setOnMenuItemClickListener {
            val fid = navController.currentDestination?.id
            fid?.let {
                when (fid) {
                    R.id.settingsFragment -> navController.navigate(R.id.action_settingsFragment_to_homeFragment)
                    R.id.profileFragment -> navController.navigate(R.id.action_profileFragment_to_homeFragment)
                }
                item.isChecked = true
            }
            true
        }
    }

    private fun defineNavBarSettings(item: MenuItem) {
        item.setOnMenuItemClickListener {
            val fid = navController.currentDestination?.id
            fid?.let {
                when (fid) {
                    R.id.homeFragment -> navController.navigate(R.id.action_homeFragment_to_settingsFragment)
                    R.id.profileFragment -> navController.navigate(R.id.action_profileFragment_to_settingsFragment)
                }
                item.isChecked = true
            }
            true
        }
    }

    private fun defineNavBarProfile(item: MenuItem) {
        item.setOnMenuItemClickListener {
            val fid = navController.currentDestination?.id
            fid?.let {
                when (fid) {
                    R.id.settingsFragment -> navController.navigate(R.id.action_settingsFragment_to_profileFragment)
                    R.id.homeFragment -> navController.navigate(R.id.action_homeFragment_to_profileFragment)
                }
                item.isChecked = true
            }
            true
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}