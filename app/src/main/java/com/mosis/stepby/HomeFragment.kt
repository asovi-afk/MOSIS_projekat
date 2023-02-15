package com.mosis.stepby

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.mosis.stepby.viewmodels.MainActivityViewModel

class HomeFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        mainVM.showBNV.value = true

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    companion object {
        const val TAG = "HomeFragment"
    }
}