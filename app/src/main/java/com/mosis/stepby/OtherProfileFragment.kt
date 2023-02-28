package com.mosis.stepby

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.FragmentOtherProfileBinding
import com.mosis.stepby.utils.*
import com.mosis.stepby.utils.adapters.FriendAdapter
import com.mosis.stepby.utils.adapters.RunAdapter
import com.mosis.stepby.viewmodels.MainActivityViewModel
import com.mosis.stepby.viewmodels.OtherProfileFragmentViewModel


class OtherProfileFragment : Fragment() {

    private val myEmail = Firebase.auth.currentUser!!.email!!
    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var viewModel: OtherProfileFragmentViewModel
    private lateinit var binding: FragmentOtherProfileBinding

    private lateinit var userBasicInfoObserver: Observer<BasicProfileInfo>
    private lateinit var runListObserver: Observer<List<RunInfo>>
    private lateinit var selectedTabObserver: Observer<ProfileTab>
    private lateinit var friendListObserver: Observer<List<FriendInfo>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainVM.locked.value = true
        viewModel = ViewModelProvider(this).get(OtherProfileFragmentViewModel::class.java)
        viewModel.update(mainVM.userPreviewEmail!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        generateObservers()

        binding = FragmentOtherProfileBinding.inflate(inflater, container, false)

        // Toggle visibility of rvPendingFriendList and rvFriendList
        binding.tvRuns.setOnClickListener { viewModel.selectedTab.value = ProfileTab.RUNS }
        binding.tvTracks.setOnClickListener { viewModel.selectedTab.value = ProfileTab.TRACKS }
        binding.tvFriends.setOnClickListener { viewModel.selectedTab.value = ProfileTab.FRIENDS }
        binding.ivSendFriendRequest.setOnClickListener { viewModel.addFriend(); binding.ivSendFriendRequest.visibility = View.GONE }

        viewModel.userBasicInfo.observe(viewLifecycleOwner, userBasicInfoObserver)
        viewModel.instantToast.observe(viewLifecycleOwner) { msg -> if (!msg.isNullOrBlank()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        viewModel.runList.observe(viewLifecycleOwner, runListObserver)
        viewModel.selectedTab.observe(viewLifecycleOwner, selectedTabObserver)
        viewModel.friendList.observe(viewLifecycleOwner, friendListObserver)

        viewModel.friendList.observe(viewLifecycleOwner, Observer { friendList ->
            binding.ivSendFriendRequest.visibility = if (friendList.firstOrNull { info -> info.email == myEmail } == null) View.VISIBLE else View.GONE
        })

        return binding.root
    }

    override fun onDestroyView() {
        viewModel.clearInstantToast()
        super.onDestroyView()
    }

    override fun onDestroy() {
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun generateObservers() {
        userBasicInfoObserver = Observer { info ->
            if (info != null) {
                binding.run {
                    tvEmail.text = info.email ?: getString(R.string.email_unknown)
                    tvUsername.text = info.username ?: getString(R.string.username_unknown)
                    tvDistance.text = if (info.distance != null) distanceToString(info.distance)  else getString(R.string.distance_unknown)
                    tvRanking.text = if (info.ranking != null) String.format("# %d", info.ranking) else getString(R.string.rank_unknown)
                    binding.ivProfile.setImageBitmap(info.bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.ic_baseline_person_24))
                }

                mainVM.locked.value = false
            }
        }

        runListObserver = Observer { list ->
            val runAdapter = RunAdapter(list)
            runAdapter.setOnItemClickListener(object: RunAdapter.ClickListener {
                override fun onClick(pos: Int, view: View) {
                    Toast.makeText(context, list[pos].name + " " + pos.toString(), Toast.LENGTH_SHORT).show()
                }
            })
            binding.rvRunList.adapter = runAdapter
        }

        selectedTabObserver = Observer { tab ->
            binding.run {

                tabUnselected(tvRuns)
                tabUnselected(tvTracks)
                tabUnselected(tvFriends)

                rvRunList.visibility = View.GONE
                rvTrackList.visibility = View.GONE
                rvFriendList.visibility = View.GONE

                when(tab!!) {
                    ProfileTab.RUNS -> { rvRunList.visibility = View.VISIBLE; tabSelected(tvRuns) }
                    ProfileTab.TRACKS -> { rvTrackList.visibility = View.VISIBLE; tabSelected(tvTracks) }
                    ProfileTab.FRIENDS -> { rvFriendList.visibility = View.VISIBLE; tabSelected(tvFriends) }
                }
            }
        }

        friendListObserver = Observer { list ->
            val friendAdapter = FriendAdapter(list)
            friendAdapter.setOnItemClickListener(object: FriendAdapter.ClickListener {
                override fun onClick(pos: Int, view: View) {
                    val email = list[pos].email
                    if (email != myEmail) {
                        mainVM.userPreviewEmail = list[pos].email
                        findNavController().navigate(R.id.action_otherProfileFragment_self)
                    }
                    else findNavController().navigate(R.id.action_otherProfileFragment_to_profileFragment)
                }
            })
            binding.rvFriendList.adapter = friendAdapter
        }
    }

    private fun tabUnselected(tv: TextView) {
        tv.setBackgroundColor(ContextCompat.getColor(context!!, ProfileTabConfig.BACKGROUND))
        tv.setTextColor(ContextCompat.getColor(context!!, ProfileTabConfig.TEXT))
    }

    private fun tabSelected(tv: TextView) {
        tv.setBackgroundColor(ContextCompat.getColor(context!!, ProfileTabConfig.SELECTED_BACKGROUND))
        tv.setTextColor(ContextCompat.getColor(context!!, ProfileTabConfig.SELECTED_TEXT))
    }

    companion object {
        const val TAG = "OtherProfileFragment"
    }
}