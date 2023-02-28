package com.mosis.stepby.utils.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mosis.stepby.R
import com.mosis.stepby.utils.FriendInfo

class FriendAdapter(var list: List<FriendInfo>, val accept: Boolean = false, val cancel: Boolean = false): RecyclerView.Adapter<FriendAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return ViewHolder(layoutInflater.inflate(R.layout.one_friend, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun setOnItemClickListener(clickListener: ClickListener) {
        mClickListener = clickListener
    }
    fun setOnCancelClickListener(clickListener: OptionClickListener) {
        mOnCancelClickListener = clickListener
    }
    fun setOnAcceptClickListener(clickListener: OptionClickListener) {
        mOnAcceptClickListener = clickListener
    }


    lateinit var mClickListener: ClickListener
    lateinit var mOnCancelClickListener: OptionClickListener
    lateinit var mOnAcceptClickListener: OptionClickListener
    interface ClickListener { fun onClick(pos: Int, view: View)}
    interface OptionClickListener { fun onClick(pos: Int, views: List<View>)}

    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val ivProfile = itemView.findViewById<ImageView>(R.id.ivProfile)
        val tvUsername = itemView.findViewById<TextView>(R.id.tvUsername)
        val tvEmail = itemView.findViewById<TextView>(R.id.tvEmail)
        val ivAccept = itemView.findViewById<ImageView>(R.id.ivAccept)
        val ivCancel = itemView.findViewById<ImageView>(R.id.ivCancel)

        init {
            itemView.setOnClickListener(this)
            if (accept) { ivAccept.visibility = View.VISIBLE; ivAccept.setOnClickListener { mOnAcceptClickListener.onClick(adapterPosition, listOf(ivAccept, ivCancel)) }}
            if (cancel) { ivCancel.visibility = View.VISIBLE; ivCancel.setOnClickListener { mOnCancelClickListener.onClick(adapterPosition, listOf(ivAccept, ivCancel)) }}
        }

        fun bind(model: FriendInfo) {
            ivProfile.setImageBitmap(model.picture)
            tvEmail.text = model.email
            tvUsername.text = model.username
        }

        override fun onClick(p0: View?) {
            mClickListener.onClick(adapterPosition, itemView)
        }
    }
}