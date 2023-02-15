package com.mosis.stepby

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.mosis.stepby.databinding.FragmentPersonalizeBinding
import com.mosis.stepby.viewmodels.MainActivityViewModel
import com.mosis.stepby.viewmodels.PersonalizeFragmentViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class PersonalizeFragment : Fragment() {

    private lateinit var binding: FragmentPersonalizeBinding
    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var viewModel: PersonalizeFragmentViewModel
    private lateinit var navController: NavController
    private lateinit var getCameraImage: ActivityResultLauncher<Void?>

    private var coroutineScope = CoroutineScope(Dispatchers.Main)

    private var profilePictureBM: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(PersonalizeFragmentViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        navController = findNavController()
        binding = FragmentPersonalizeBinding.inflate(inflater, container, false)

        getCameraImage = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
                bitmap ->
            bitmap?.let {
                Log.d(TAG, "took a photo")
                val image = binding.ivProfile
                Log.d(TAG, "maxWidth: ${image.maxWidth}, width: ${image.width}, maxHeight: ${image.maxHeight}, height: ${image.height}")
                Log.d(TAG, "x: ${image.x}, y: ${image.y}")
                Log.d(TAG, "BITMAP width: ${it.width}, height: ${it.height} ")

                profilePictureBM = Bitmap.createBitmap(it, 0, 0, it.width, it.height)
                image.setImageBitmap(profilePictureBM)
            }
        }

        binding.ivCamera.setOnClickListener {
            getCameraImage.launch(null)
        }

        binding.btnGetStarted.setOnClickListener {
            mainVM.locked.value = true

            val username = binding.editUsername.text.toString()
            val fullName = binding.editFullName.text.toString()
            val phone = binding.editPhone.text.toString()

            coroutineScope.launch(Dispatchers.IO) {
                viewModel.updateProfile(username = username, fullName = fullName, phone = phone, profilePicture = profilePictureBM)
            }
        }

        viewModel.instantToast.observe(viewLifecycleOwner, Observer { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        })

        viewModel.responseStatus.observe(viewLifecycleOwner, Observer { response ->
            if (response.success) {
                navController.navigate((R.id.action_personalizeFragment_to_homeFragment))
            } else {
                Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
            }

            mainVM.locked.value = false
        })

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModelStore.clear()
        coroutineScope.cancel()
    }

    companion object {
        const val TAG = "PersonalizeFragment"
    }
}