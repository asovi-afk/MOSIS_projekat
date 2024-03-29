package com.mosis.stepby

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.mosis.stepby.databinding.FragmentProfileBinding
import com.mosis.stepby.utils.*
import com.mosis.stepby.utils.adapters.FriendAdapter
import com.mosis.stepby.utils.adapters.RunAdapter
import com.mosis.stepby.viewmodels.MainActivityViewModel
import com.mosis.stepby.viewmodels.ProfileFragmentViewModel

class ProfileFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private val viewModel: ProfileFragmentViewModel by activityViewModels()
    private lateinit var binding: FragmentProfileBinding

    private lateinit var userBasicInfoObserver: Observer<BasicProfileInfo>
    private lateinit var runListObserver: Observer<List<RunInfo>>
    private lateinit var selectedTabObserver: Observer<ProfileTab>
    private lateinit var friendListObserver: Observer<List<FriendInfo>>
    private lateinit var pendingFriendListObserver: Observer<List<FriendInfo>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainVM.locked.value = true

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Function must be called here because it requires context!!
        generateObservers()

        binding = FragmentProfileBinding.inflate(inflater, container, false)

        // NOTE:
        // Updated action is really expensive! Consumes many firestore reads
        binding.ivUpdateRanking.setOnClickListener { mainVM.locked.value = true; viewModel.update() }
        // Old action
        //binding.ivUpdateRanking.setOnClickListener { mainVM.locked.value = true; viewModel.updateRanking() }

        // Toggle visibility of rvPendingFriendList and rvFriendList
        binding.tvPendingFriendList.setOnClickListener { binding.rvPendingFriendList.run { visibility = if (visibility == View.GONE) View.VISIBLE else View.GONE } }
        binding.tvFriendList.setOnClickListener { binding.rvFriendList.run { visibility = if (visibility == View.GONE) View.VISIBLE else View.GONE } }
        binding.tvRuns.setOnClickListener { viewModel.selectedTab.value = ProfileTab.RUNS }
        binding.tvTracks.setOnClickListener { viewModel.selectedTab.value = ProfileTab.TRACKS }
        binding.tvFriends.setOnClickListener { viewModel.selectedTab.value = ProfileTab.FRIENDS }

        viewModel.userBasicInfo.observe(viewLifecycleOwner, userBasicInfoObserver)
        viewModel.instantToast.observe(viewLifecycleOwner) { msg -> if (!msg.isNullOrBlank()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        viewModel.runList.observe(viewLifecycleOwner, runListObserver)
        viewModel.selectedTab.observe(viewLifecycleOwner, selectedTabObserver)
        viewModel.friendList.observe(viewLifecycleOwner, friendListObserver)
        viewModel.pendingFriendList.observe(viewLifecycleOwner, pendingFriendListObserver)
        return binding.root
    }

    override fun onDestroyView() {
        viewModel.clearInstantToast()
        super.onDestroyView()
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
                tvPendingFriendList.visibility = View.GONE
                rvPendingFriendList.visibility = View.GONE
                tvFriendList.visibility = View.GONE
                rvFriendList.visibility = View.GONE

                when(tab!!) {
                    ProfileTab.RUNS -> { rvRunList.visibility = View.VISIBLE; tabSelected(tvRuns) }
                    ProfileTab.TRACKS -> { rvTrackList.visibility = View.VISIBLE; tabSelected(tvTracks) }
                    ProfileTab.FRIENDS -> {
                        tvPendingFriendList.visibility = View.VISIBLE
                        tvFriendList.visibility = View.VISIBLE
                        rvFriendList.visibility = View.VISIBLE

                        tabSelected(tvFriends)
                    }
                }
            }
        }

        friendListObserver = Observer { list ->
            val friendAdapter = FriendAdapter(list, cancel = true)
            friendAdapter.setOnCancelClickListener(object: FriendAdapter.OptionClickListener {
                override fun onClick(pos: Int, views: List<View>) {
                    viewModel.removeFriend(list[pos].email, views)
                }
            })
            friendAdapter.setOnItemClickListener(object: FriendAdapter.ClickListener {
                override fun onClick(pos: Int, view: View) {
                    mainVM.userPreviewEmail = list[pos].email
                    findNavController().navigate(R.id.action_profileFragment_to_otherProfileFragment)
                }
            })
            binding.rvFriendList.adapter = friendAdapter
        }

        pendingFriendListObserver = Observer { list ->
            val pendingFriendAdapter = FriendAdapter(list, true, true)
            pendingFriendAdapter.setOnCancelClickListener(object: FriendAdapter.OptionClickListener {
                override fun onClick(pos: Int, views: List<View>) {
                    viewModel.declineFriendship(list[pos].email, views)
                }
            })
            pendingFriendAdapter.setOnAcceptClickListener(object : FriendAdapter.OptionClickListener {
                override fun onClick(pos: Int, views: List<View>) {
                    viewModel.acceptFriendship(list[pos].email, views)
                }
            })
            pendingFriendAdapter.setOnItemClickListener(object: FriendAdapter.ClickListener {
                override fun onClick(pos: Int, view: View) {
                    mainVM.userPreviewEmail = list[pos].email
                    findNavController().navigate(R.id.action_profileFragment_to_otherProfileFragment)
                }
            })
            binding.rvPendingFriendList.adapter = pendingFriendAdapter
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
        private const val TAG = "ProfileFragment"


    }
}